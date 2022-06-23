package com.example.bikeapp.db;

import androidx.annotation.NonNull;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

public class Segment {
    private int tripID;
    private double lat1;
    private double lon1;
    private double lat2;
    private double lon2;
    private double zRMSAccel;

    public Segment(int tripID, @NonNull LocationData loc1, @NonNull LocationData loc2, double z) {
        this.tripID = tripID;
        this.lat1 = loc1.getLatitude();
        this.lon1 = loc1.getLongitude();
        this.lat2 = loc2.getLatitude();
        this.lon2 = loc2.getLongitude();
        this.zRMSAccel = z;
    }

    public double getLat1() {
        return lat1;
    }

    public double getLon1() {
        return lon1;
    }

    public double getLat2() {
        return lat2;
    }

    public double getLon2() {
        return lon2;
    }

    public double getzRMSAccel() {
        return zRMSAccel;
    }

    public Polyline toPolyline() {
        List<GeoPoint> points = new ArrayList<>();
        points.add(new GeoPoint(lat1, lon1));
        points.add(new GeoPoint(lat2, lon2));
        Polyline line = new Polyline();
        line.setPoints(points);
        return line;
    }
}
