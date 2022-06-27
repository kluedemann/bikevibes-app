package com.example.bikeapp.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity(primaryKeys = {"tripID", "ts1"})
public class Segment {
    private int tripID;

    @NonNull
    private Date ts1;
    private double lat1;
    private double lon1;

    @NonNull
    private Date ts2;
    private double lat2;
    private double lon2;

    private double rmsZAccel;
    private double maxZAccel;

    public Segment(int tripID, @NonNull LocationData loc1, @NonNull LocationData loc2, double rms, double max) {
        this.tripID = tripID;
        this.ts1 = loc1.getTimestamp();
        this.lat1 = loc1.getLatitude();
        this.lon1 = loc1.getLongitude();
        this.ts2 = loc2.getTimestamp();
        this.lat2 = loc2.getLatitude();
        this.lon2 = loc2.getLongitude();
        this.rmsZAccel = rms;
        this.maxZAccel = max;
    }

    public Segment(int tripID, @NonNull Date ts1, double lat1, double lon1, @NonNull Date ts2, double lat2, double lon2, double rmsZAccel, double maxZAccel) {
        this.tripID = tripID;
        this.ts1 = ts1;
        this.lat1 = lat1;
        this.lon1 = lon1;
        this.ts2 = ts2;
        this.lat2 = lat2;
        this.lon2 = lon2;
        this.rmsZAccel = rmsZAccel;
        this.maxZAccel = maxZAccel;
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

    public double getRmsZAccel() {
        return rmsZAccel;
    }

    public int getTripID() {
        return tripID;
    }

    @NonNull
    public Date getTs1() {
        return ts1;
    }

    @NonNull
    public Date getTs2() {
        return ts2;
    }

    public double getMaxZAccel() {
        return maxZAccel;
    }

    public LocationData getLoc1() {
        return new LocationData(ts1, lat1, lon1, tripID);
    }

    public LocationData getLoc2() {
        return new LocationData(ts2, lat2, lon2, tripID);
    }

    public Polyline toPolyline() {
        List<GeoPoint> points = new ArrayList<>();
        points.add(new GeoPoint(lat1, lon1));
        points.add(new GeoPoint(lat2, lon2));
        Polyline line = new Polyline();
        line.setPoints(points);
        return line;
    }

    public void setTripID(int tripID) {
        this.tripID = tripID;
    }

    public void setLat1(double lat1) {
        this.lat1 = lat1;
    }

    public void setLon1(double lon1) {
        this.lon1 = lon1;
    }

    public void setLat2(double lat2) {
        this.lat2 = lat2;
    }

    public void setLon2(double lon2) {
        this.lon2 = lon2;
    }

    public void setRmsZAccel(double rmsZAccel) {
        this.rmsZAccel = rmsZAccel;
    }

    public void setTs1(@NonNull Date ts1) {
        this.ts1 = ts1;
    }

    public void setTs2(@NonNull Date ts2) {
        this.ts2 = ts2;
    }

    public void setMaxZAccel(double maxZAccel) {
        this.maxZAccel = maxZAccel;
    }
}
