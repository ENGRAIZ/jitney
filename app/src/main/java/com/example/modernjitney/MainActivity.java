package com.example.modernjitney;



import androidx.appcompat.app.AppCompatActivity;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
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
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.TravelMode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;


public class MainActivity extends AppCompatActivity {

    private FirebaseDatabase mDatabase;
    private DatabaseReference mRef;
    private MapView mMapView;
    private GoogleMap mMap;
    private Marker Marker;
    private com.google.android.gms.maps.model.Marker busStopMarker;
    private com.google.android.gms.maps.model.Marker busStopMarker2;
    LatLng busStopLocation1 = new LatLng(16.408796647051457, 120.5984680194007);
    LatLng busStopLocation2 = new LatLng(16.412705883954, 120.59549831791495);

    String etaTime2;
    String etaTime3;
    SimpleDateFormat dateFormat = new SimpleDateFormat("h:mm a");

    int passenger;





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FirebaseApp.initializeApp(this);
        mMapView = (MapView) findViewById(R.id.map);
        mMapView.onCreate(savedInstanceState);
        mMapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                mMap = googleMap;
            }
        });

        // Initialize Firebase Database
        mDatabase = FirebaseDatabase.getInstance();
        mRef = mDatabase.getReference("");



        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
        // Retrieve GPS data from Firebase in real-time
        mRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // Get the GPS data from Firebase
                if (dataSnapshot.hasChild("latitude") && dataSnapshot.hasChild("longitude/")) {
                    Double latitude = dataSnapshot.child("latitude").getValue(Double.class);
                    Double longitude = dataSnapshot.child("longitude").getValue(Double.class);
                    if (latitude != null && longitude != null) {
                        updateMap(latitude, longitude);
                    if (dataSnapshot.hasChild("Passenger/counter")) {
                        int passengerCount = dataSnapshot.child("Passenger/counter").getValue(Integer.class);
                        passenger = passengerCount;

                        Bitmap originalBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bus_stop_icon);
                        int width = originalBitmap.getWidth();
                        int height = originalBitmap.getHeight();
                        float scale = .15f; // change the scale factor as needed
                        Matrix matrix = new Matrix();
                        matrix.postScale(scale, scale);
                        Bitmap scaledBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, width, height, matrix, false);
                        busStopMarker = mMap.addMarker(new MarkerOptions().position(busStopLocation1).title("Bus Stop").icon(BitmapDescriptorFactory.fromBitmap(scaledBitmap))
                                .snippet("Click to view ETA"));
                        busStopMarker.setTag("bus_stop");

                        // Calculate the ETA using Google Maps Directions API
                        LatLng origin = new LatLng(latitude, longitude);
                        LatLng destination = busStopLocation1;
                        String apiKey = "AIzaSyCj96nOAFv4eiPvM20ZQjLvIcg4dlmkn3A"; // Replace with your Google Maps API key
                        GeoApiContext context = new GeoApiContext.Builder()
                                .apiKey(apiKey)
                                .build();
                        DirectionsApiRequest request = DirectionsApi.newRequest(context)
                                .origin(origin.latitude + "," + origin.longitude)
                                .destination(destination.latitude + "," + destination.longitude)
                                .mode(TravelMode.DRIVING)
                                .avoid(DirectionsApi.RouteRestriction.TOLLS);
                        try {
                            DirectionsResult result = request.await();
                            if (result.routes.length > 0 && result.routes[0].legs.length > 0) {
                                long seconds = result.routes[0].legs[0].duration.inSeconds;
                                long etaMillis = System.currentTimeMillis() + (seconds * 1000);
                                String etaTime = dateFormat.format(new Date(etaMillis));
                                etaTime2 = etaTime;
                                Log.d("ETA", "Estimated time of arrival at bus stop: " + etaTime);

                                // Update the TextView with the ETA
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        Bitmap originalBitmap2 = BitmapFactory.decodeResource(getResources(), R.drawable.bus_stop_icon);
                        int width2 = originalBitmap.getWidth();
                        int height2 = originalBitmap.getHeight();
                        float scale2 = .15f; // change the scale factor as needed
                        Matrix matrix2 = new Matrix();
                        matrix2.postScale(scale2, scale2);
                        Bitmap scaledBitmap2 = Bitmap.createBitmap(originalBitmap2, 0, 0, width2, height2, matrix2, false);
                        busStopMarker2 = mMap.addMarker(new MarkerOptions().position(busStopLocation2).title("Bus Stop").icon(BitmapDescriptorFactory.fromBitmap(scaledBitmap2))
                                .snippet("Click to view ETA"));
                        busStopMarker2.setTag("bus_stop2");
                        LatLng destination2 = busStopLocation2;
                        DirectionsApiRequest request2 = DirectionsApi.newRequest(context)
                                .origin(origin.latitude + "," + origin.longitude)
                                .destination(destination2.latitude + "," + destination2.longitude)
                                .mode(TravelMode.DRIVING)
                                .avoid(DirectionsApi.RouteRestriction.TOLLS);
                        try {
                            DirectionsResult result2 = request2.await();
                            if (result2.routes.length > 0 && result2.routes[0].legs.length > 0) {
                                long seconds2 = result2.routes[0].legs[0].duration.inSeconds;
                                long etaMillis2 = System.currentTimeMillis() + (seconds2 * 1000);
                                String etaTime2 = dateFormat.format(new Date(etaMillis2));
                                etaTime3 = etaTime2;
                                Log.d("ETA", "Estimated time of arrival at bus stop: " + etaTime2);

                                // Update the TextView with the ETA
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
;
                        }
                    }
                }
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Handle errors
            }
        });
        handler.postDelayed(this, 60000);
        }
        }, 0);


    }


    private void updateMap(double latitude, double longitude) {
        dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Manila"));
        // Add a marker to the map at the specified GPS location
        LatLng busLocation = new LatLng(latitude, longitude);
        if (Marker != null) {
            Marker.remove();
        }
        //Marker = mMap.addMarker(new MarkerOptions().position(busLocation).title("Bus Location"));

        //mMap.moveCamera(CameraUpdateFactory.newLatLng(busLocation));
        //float zoomLevel = 18.0f;
        //CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(busStopLocation1, zoomLevel);
        //mMap.animateCamera(cameraUpdate);

        // Set a click listener for the bus stop marker
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                // Check if the clicked marker is the bus stop marker
                if (marker.getTag() != null && marker.getTag().equals("bus_stop")) {
                    // Update the ETA TextView with the calculated ETA
                    TextView etaTextView = findViewById(R.id.textView3);
                    etaTextView.setText(etaTime2);
                    etaTextView.setVisibility(View.VISIBLE);
                    TextView passengerCountTextView = findViewById(R.id.textView4);
                    passengerCountTextView.setText(String.valueOf(passenger));
                    passengerCountTextView.setVisibility(View.VISIBLE);
                    return true;
                }
                else if (marker.getTag() != null && marker.getTag().equals("bus_stop2")){
                    TextView etaTextView = findViewById(R.id.textView3);
                    etaTextView.setText(etaTime3);
                    etaTextView.setVisibility(View.VISIBLE);
                    TextView passengerCountTextView = findViewById(R.id.textView4);
                    passengerCountTextView.setText(String.valueOf(passenger));
                    passengerCountTextView.setVisibility(View.VISIBLE);
                    return true;
                }
                return false;
            }
        });

    }
    @Override
    protected void onResume() {
        super.onResume();
        mMapView.onResume();
    }
    @Override
    protected void onPause() {
        super.onPause();
// ...
        mMapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
// ...
        mMapView.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
// ...
        mMapView.onLowMemory();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
// ...
        mMapView.onSaveInstanceState(outState);
    }
}