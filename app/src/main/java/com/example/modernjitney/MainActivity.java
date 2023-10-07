package com.example.modernjitney;

import static android.content.ContentValues.TAG;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.maps.DirectionsApi;
import com.google.maps.DirectionsApiRequest;
import com.google.maps.GeoApiContext;
import com.google.maps.errors.ApiException;
import com.google.maps.model.DirectionsLeg;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.model.Duration;
import com.google.maps.model.TravelMode;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity implements GoogleMap.OnMarkerClickListener {

    private DatabaseReference mRef;

    private MapView mMapView;
    private GoogleMap mMap;
    private Marker[] markers = new Marker[3];

    private WebSocketManager webSocketManager;

    private LatLngWithLabel[] busStopLocations = {
            new LatLngWithLabel(16.408796647051457, 120.5984680194007, "University of the Cordilleras"),
            new LatLngWithLabel(16.412601, 120.595507, "Harrison Road"),
            new LatLngWithLabel(16.408810, 120.600080, "SM Baguio City"),
            new LatLngWithLabel(16.419015, 120.597006, "SLU"),
            new LatLngWithLabel(16.421636, 120.600045, "7/11 Rimando Road")
    };

    private String[] etaTimes = new String[3];
    private int[] passengerCounts = new int[3];

    private double[] latitudes = new double[3];
    private double[] longitudes = new double[3];
    private int[] passengers = new int[3];

    private boolean zoomedToFirstBus = false;

    private HashMap<Marker, TextView> busNumberTextViews = new HashMap<>();

    @SuppressLint("SimpleDateFormat")
    private SimpleDateFormat dateFormat = new SimpleDateFormat("h:mm a");

    @SuppressLint("PotentialBehaviorOverride")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webSocketManager = new WebSocketManager();
        webSocketManager.startWebSocket();

        FirebaseApp.initializeApp(this);
        mMapView = findViewById(R.id.map);
        mMapView.onCreate(savedInstanceState);
        mMapView.getMapAsync(googleMap -> {
            mMap = googleMap;
            mMap.setOnMarkerClickListener(MainActivity.this);
        });
        Button helpButton = findViewById(R.id.helpButton);
        helpButton.setOnClickListener(v -> showLegends());

        // Initialize Firebase Database
        FirebaseDatabase mDatabase = FirebaseDatabase.getInstance();
        mRef = mDatabase.getReference("");

        RetrieveGPSDataTask task = new RetrieveGPSDataTask();
        task.execute();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Close the WebSocket connection when the activity is destroyed
        mMapView.onDestroy();
        webSocketManager.closeWebSocket();
    }

    private class RetrieveGPSDataTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {

            if (!isNetworkAvailable()) {
                runOnUiThread(this::showNoInternetAlert);
                return null;
            }
            // Retrieve GPS data from Firebase in the background
            mRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    // Get the GPS data from Firebase
                    int i = 0;
                    for (DataSnapshot child : dataSnapshot.getChildren()) {
                        String busName = child.getKey();
                        latitudes[i] = child.child("latitude").getValue(Double.class);
                        longitudes[i] = child.child("longitude").getValue(Double.class);
                        passengers[i] = child.child("Passenger/counter").getValue(Integer.class);
                        i++;
                    }

                    // Update map with the latest GPS data
                    runOnUiThread(() -> {
                        updateMap(latitudes, longitudes, passengers);

                        if (!zoomedToFirstBus) {
                            if (latitudes.length > 0 && longitudes.length > 0) {
                                LatLng firstLatLng = new LatLng(latitudes[0], longitudes[0]);
                                CameraPosition cameraPosition = new CameraPosition.Builder()
                                        .target(firstLatLng)
                                        .zoom(14)
                                        .build();
                                mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

                                zoomedToFirstBus = true;
                            }
                        }
                    });
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Log.w(TAG, "Failed to read value.", databaseError.toException());
                }
            });

            return null;
        }
        private boolean isNetworkAvailable() {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager != null) {
                NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                return activeNetworkInfo != null && activeNetworkInfo.isConnected();
            }
            return false;
        }

        // Helper method to show a pop-up alert for no internet connection
        private void showNoInternetAlert() {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("No Internet Connection");
            builder.setMessage("Please check your internet connection and try again.");
            builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }


    private void updateMap(double[] latitudes, double[] longitudes, int[] passengers) {
        // Remove previous markers and text views
        for (Marker marker : markers) {
            if (marker != null) {
                marker.remove();
            }
        }
        Arrays.fill(markers, null);
        busNumberTextViews.clear(); // Clear the HashMap

        // Add new markers for bus locations
        for (int i = 0; i < latitudes.length; i++) {
            LatLng latLng = new LatLng(latitudes[i], longitudes[i]);

            // Set marker icon
            Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.bus);
            icon = resizeBitmap(icon, 100, 100);

            MarkerOptions markerOptions = new MarkerOptions()
                    .position(latLng)
                    .icon(BitmapDescriptorFactory.fromBitmap(icon))
                    .snippet("Bus " + (i + 1));
            Marker marker = mMap.addMarker(markerOptions); // Store the marker in a variable
            markers[i] = marker; // Store the marker in the markers array

            // Add TextView below the marker
            TextView textView = new TextView(this);
            textView.setText("Bus " + (i + 1));
            textView.setTextColor(Color.BLACK);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            textView.setGravity(Gravity.CENTER);

            // Set padding for the text view
            int padding = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
            textView.setPadding(padding, padding, padding, padding);

            Bitmap textViewBitmap = createBitmapFromView(textView);

            Bitmap imageIcon = BitmapFactory.decodeResource(getResources(), R.drawable.bus);
            imageIcon = resizeBitmap(imageIcon, 100, 100);

            Bitmap compositeBitmap = Bitmap.createBitmap(imageIcon.getWidth(), imageIcon.getHeight() + 50, imageIcon.getConfig());

            Canvas canvas = new Canvas(compositeBitmap);

            canvas.drawBitmap(imageIcon, 0, 0, null);

            Paint textPaint = new Paint();
            textPaint.setColor(Color.BLACK);
            textPaint.setTextSize(30);
            textPaint.setTextAlign(Paint.Align.CENTER);

            String text = "Bus " + (i + 1);

            int textX = imageIcon.getWidth() / 2;
            int textY = imageIcon.getHeight() + 10;

            canvas.drawText(text, textX, textY, textPaint);

            marker.setIcon(BitmapDescriptorFactory.fromBitmap(compositeBitmap));

            // Store the marker and text view in the HashMap for future reference
            busNumberTextViews.put(marker, textView);

        }

        // Add markers for bus stop locations
        for (LatLngWithLabel busStopLatLng : busStopLocations) {
            Bitmap originalBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bus_stop_icon);
            int width = originalBitmap.getWidth();
            int height = originalBitmap.getHeight();
            float scale = .10f; // change the scale factor as needed
            Matrix matrix = new Matrix();
            matrix.postScale(scale, scale);
            Bitmap scaledBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, width, height, matrix, false);
            MarkerOptions busStopMarkerOptions = new MarkerOptions()
                    .position(busStopLatLng.getLatLng())
                    .icon(BitmapDescriptorFactory.fromBitmap(scaledBitmap))
                    .title(busStopLatLng.getLabel()); // Set the title as the label
            mMap.addMarker(busStopMarkerOptions);
        }

        updateETAAndPassengerCount();
    }
    private Bitmap createBitmapFromView(View view) {
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        Bitmap bitmap = Bitmap.createBitmap(view.getMeasuredWidth(), view.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
        view.draw(canvas);
        return bitmap;
    }

    private void updateETAAndPassengerCount() {
        if (etaTimes.length > 0 && passengerCounts.length > 0) {
            String eta = etaTimes[0];
            int passengerCount = passengerCounts[0];

            if (eta != null) {
                String busNumber = "Bus " + (0 + 1); // Assuming bus numbers are sequential starting from 1
                String etaWithBusNumber = busNumber + " - " + eta;
                displayETAAndPassengerCount(0, etaWithBusNumber, passengerCount);
            } else {
                displayETAAndPassengerCount(0, "", passengerCount);
            }
        }
    }

    private void displayETAAndPassengerCount(int busIndex, String eta, int passengerCount) {
        TextView etaTextView;
        TextView passengerTextView;

        // Determine the appropriate TextViews based on the bus index
        switch (busIndex) {
            case 0:
                etaTextView = findViewById(R.id.textView3);
                passengerTextView = findViewById(R.id.textView4);
                break;
            case 1:
                etaTextView = findViewById(R.id.textView5);
                passengerTextView = findViewById(R.id.textView6);
                break;
            case 2:
                etaTextView = findViewById(R.id.textView7);
                passengerTextView = findViewById(R.id.textView8);
                break;
            default:
                return; // Invalid bus index
        }

        if (eta == "") {
            etaTextView.setText("");
        } else {
            String busNumber = "Bus " + (busIndex + 1); // Assuming bus numbers are sequential starting from 1
            String etaWithBusNumber = busNumber + " - " + eta;

            etaTextView.setText(etaWithBusNumber);

            // Update passenger count and text color
            passengerTextView.setText(String.valueOf(passengerCount));
            if (passengerCount <= 0) {
                passengerTextView.setTextColor(Color.RED);
            } else {
                // If the passenger count is greater than 0, set the text color back to the default color (black)
                passengerTextView.setTextColor(Color.BLACK);
            }
        }
    }



    private DirectionsResult getDirectionsResult(LatLng origin, LatLng destination) {
        GeoApiContext context = new GeoApiContext.Builder()
                .apiKey("AIzaSyCj96nOAFv4eiPvM20ZQjLvIcg4dlmkn3A") // Replace with your own API key
                .build();

        DirectionsApiRequest request = DirectionsApi.getDirections(context,
                        origin.latitude + "," + origin.longitude,
                        destination.latitude + "," + destination.longitude)
                .mode(TravelMode.DRIVING);

        try {
            return request.await();
        } catch (ApiException | InterruptedException | IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private String calculateETA(DirectionsResult directionsResult) {
        if (directionsResult != null) {
            DirectionsRoute[] routes = directionsResult.routes;
            if (routes.length > 0) {
                DirectionsLeg leg = routes[0].legs[0];
                Duration duration = leg.duration;

                // Convert duration value (in seconds) to milliseconds
                long durationMillis = duration.inSeconds * 1000;

                // Get the current time
                long currentTimeMillis = System.currentTimeMillis();

                // Add the duration to the current time
                long arrivalTimeMillis = currentTimeMillis + durationMillis;

                // Create a Date object with the arrival time
                Date arrivalTime = new Date(arrivalTimeMillis);

                // Format the arrival time to display only the time
                SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a");
                timeFormat.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));
                String formattedTime = timeFormat.format(arrivalTime);

                return formattedTime;
            }
        }
        return "";
    }


    private Bitmap resizeBitmap(Bitmap bitmap, int width, int height) {
        return Bitmap.createScaledBitmap(bitmap, width, height, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mMapView.onPause();
    }


    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mMapView.onSaveInstanceState(outState);
    }

    @Override
    public boolean onMarkerClick(Marker marker) {

        if (marker.getTitle() != null) {
            // Display the name of the bus stop location
            Toast.makeText(this, marker.getTitle(), Toast.LENGTH_SHORT).show();
        }

        LatLng busStopLatLng = marker.getPosition();

        // Calculate ETA for each bus from the clicked bus stop
        for (int i = 0; i < latitudes.length; i++) {
            LatLng busLatLng = new LatLng(latitudes[i], longitudes[i]);

            // Get DirectionsResult from Google Maps Directions API
            DirectionsResult directionsResult = getDirectionsResult(busLatLng, busStopLatLng);

            // Calculate ETA from the DirectionsResult
            String eta = calculateETA(directionsResult);

            // Update etaTimes and passengerCounts arrays with the calculated values
            etaTimes[i] = eta;
            passengerCounts[i] = passengers[i];

            // Update TextView with ETA and passenger count for each bus
            displayETAAndPassengerCount(i, eta, passengers[i]);
        }

        return true;
    }

    @SuppressLint("SetTextI18n")
    private void showLegends() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Legends");

        // Create a TextView to display the legends
        TextView textView = new TextView(MainActivity.this);
        textView.setText("Legends:\n" +
                "- Bus markers (Black Color) represent the current location of buses.\n" +
                "- Bus stop markers (Red Color) represent the locations of bus stops.\n" +
                "- ETA (Estimated Time of Arrival) shows the expected arrival time of buses at selected bus stops.\n" +
                "- Available Seat indicates the number of available seat on each bus.");

        textView.setPadding(20, 20, 20, 20);
        builder.setView(textView);

        // Add a button to close the dialog
        builder.setPositiveButton("Close", (dialog, which) -> dialog.dismiss());

        // Create and show the dialog
        AlertDialog dialog = builder.create();
        dialog.show();

    }
}

