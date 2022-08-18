package com.bikevibes.bikeapp.db;

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
    private Double lat1;
    private Double lon1;

    @NonNull
    private Date ts2;
    private Double lat2;
    private Double lon2;

    private Double rmsZAccel;
    private Double maxZAccel;

    public Segment(int tripID, @NonNull LocationData loc1, @NonNull LocationData loc2, Double rms, Double max) {
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

    public Segment(int tripID, @NonNull Date ts1, Double lat1, Double lon1, @NonNull Date ts2, Double lat2, Double lon2, Double rmsZAccel, Double maxZAccel) {
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

    public LocationData getLoc1() {
        return new LocationData(ts1, lat1, lon1, tripID);
    }

    public LocationData getLoc2() {
        return new LocationData(ts2, lat2, lon2, tripID);
    }

    /**
     * Create a Polyline from the Segment.
     * @return - an OSMdroid Polyline that can be added to the map
     */
    public Polyline toPolyline() {
        List<GeoPoint> points = new ArrayList<>();
        points.add(new GeoPoint(lat1, lon1));
        points.add(new GeoPoint(lat2, lon2));
        Polyline line = new Polyline();
        line.setPoints(points);
        return line;
    }

    // ***************************** Getters and Setters *******************************************

    public Double getLat1() {
        return lat1;
    }

    public Double getLon1() {
        return lon1;
    }

    public Double getLat2() {
        return lat2;
    }

    public Double getLon2() {
        return lon2;
    }

    public Double getRmsZAccel() {
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

    public Double getMaxZAccel() {
        return maxZAccel;
    }

    public void setTripID(int tripID) {
        this.tripID = tripID;
    }

    public void setLat1(Double lat1) {
        this.lat1 = lat1;
    }

    public void setLon1(Double lon1) {
        this.lon1 = lon1;
    }

    public void setLat2(Double lat2) {
        this.lat2 = lat2;
    }

    public void setLon2(Double lon2) {
        this.lon2 = lon2;
    }

    public void setRmsZAccel(Double rmsZAccel) {
        this.rmsZAccel = rmsZAccel;
    }

    public void setTs1(@NonNull Date ts1) {
        this.ts1 = ts1;
    }

    public void setTs2(@NonNull Date ts2) {
        this.ts2 = ts2;
    }

    public void setMaxZAccel(Double maxZAccel) {
        this.maxZAccel = maxZAccel;
    }
}
