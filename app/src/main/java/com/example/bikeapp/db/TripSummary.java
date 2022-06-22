package com.example.bikeapp.db;

import androidx.annotation.NonNull;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TripSummary {
    private int tripID;
    private Date start;
    private Date end;
    private double dist;
    private double speed;
    private double bumpiness;
    private double zoom;
    private GeoPoint center;
    private List<Polyline> lines;

    public TripSummary(int tripID, List<LocationData> locs, long start, long end, double bumpiness) {
        this.tripID = tripID;
        this.start = new Date(start);
        this.end = new Date(end);
        this.dist = getDist(locs);
        this.speed = getAvgSpeed(start, end);
        this.bumpiness = bumpiness;
        this.zoom = 10;
        this.center = new GeoPoint(53.5351, -113.4938);
        this.lines = new ArrayList<>();
    }

    private double getAvgSpeed(long start, long end) {
        double hours = (end - start) / (1000 * 60 * 60f);
        if (hours != 0) {
            return dist / hours;
        }
        return 0;
    }

    private double getDist(@NonNull List<LocationData> locs) {
        if (locs.size() < 2) {
            return 0;
        }
        double dist = 0;
        LocationData prev = locs.get(0);
        LocationData current;
        for (int i = 1; i < locs.size(); i++) {
            current = locs.get(i);
            dist += getPairDist(current, prev);
            prev = current;
        }
        return dist;
    }

    private double getPairDist(@NonNull LocationData loc1, @NonNull LocationData loc2) {
        final int r = 6371; // Earth's radius
        double dLat = Math.toRadians(loc2.getLatitude() - loc1.getLatitude());
        double dLon = Math.toRadians(loc2.getLongitude() - loc1.getLongitude());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.sin(dLon / 2) * Math.sin(dLon / 2)
                * Math.cos(Math.toRadians(loc1.getLatitude())) * Math.cos(Math.toRadians(loc2.getLatitude()));
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        //Log.d("Distance", String.format(Locale.getDefault(), "%f, %f, %f", dLat, dLon, r*c));
        return r * c;
    }

    public void setMap(List<Polyline> lines, GeoPoint center, double zoom) {
        this.lines = lines;
        this.center = center;
        this.zoom = zoom;
    }

    public int getTripID() {
        return tripID;
    }

    public Date getStart() {
        return start;
    }

    public Date getEnd() {
        return end;
    }

    public double getDist() {
        return dist;
    }

    public double getSpeed() {
        return speed;
    }

    public double getBumpiness() {
        return bumpiness;
    }

    public double getZoom() {
        return zoom;
    }

    public GeoPoint getCenter() {
        return center;
    }

    public List<Polyline> getLines() {
        return lines;
    }
}
