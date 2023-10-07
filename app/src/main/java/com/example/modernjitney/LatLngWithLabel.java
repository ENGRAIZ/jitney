package com.example.modernjitney;

import com.google.android.gms.maps.model.LatLng;

public class LatLngWithLabel {
    private double latitude;
    private double longitude;
    private String label;

    public LatLngWithLabel(double latitude, double longitude, String label) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public LatLng getLatLng() {
        return new LatLng(latitude, longitude);
    }
}
