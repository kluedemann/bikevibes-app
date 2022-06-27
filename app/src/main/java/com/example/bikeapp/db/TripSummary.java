package com.example.bikeapp.db;

import androidx.annotation.NonNull;

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
    private double centerLat;
    private double centerLon;

    private List<Segment> segments;

//    public TripSummary(int tripID, List<LocationData> locs, Date start, Date end, double bumpiness) {
//        this.tripID = tripID;
//        this.start = start;
//        this.end = end;
//        this.dist = getDist(locs);
//        this.speed = getAvgSpeed(start, end);
//        this.bumpiness = bumpiness;
//        this.zoom = 10;
//        this.centerLat = 53.5351;
//        this.centerLon = -113.4938;
//        this.segments = new ArrayList<>();
//    }

    public TripSummary(int tripID, List<Segment> segs, Date start, Date end, double bumpiness) {
        this.tripID = tripID;
        this.start = start;
        this.end = end;
        this.dist = getDist(segs);
        this.speed = getAvgSpeed(start, end);
        this.bumpiness = bumpiness;
        this.zoom = 10;
        this.centerLat = 53.5351;
        this.centerLon = -113.4938;
        this.segments = new ArrayList<>();
    }

    /**
     * Return the average speed over the trip in km/h.
     * @param start - the UNIX start time in milliseconds
     * @param end - the UNIX end time in milliseconds
     * @return the average speed in km/h (or zero if the duration is 0 ms)
     */
    private double getAvgSpeed(@NonNull Date start, @NonNull Date end) {
        double hours = (end.getTime() - start.getTime()) / (1000 * 60 * 60f);
        if (hours != 0) {
            return dist / hours;
        }
        return 0;
    }


//    private double getDist(@NonNull List<LocationData> locs) {
//        if (locs.size() < 2) {
//            return 0;
//        }
//        double dist = 0;
//        LocationData prev = locs.get(0);
//        LocationData current;
//        for (int i = 1; i < locs.size(); i++) {
//            current = locs.get(i);
//            dist += getPairDist(current, prev);
//            prev = current;
//        }
//        return dist;
//    }

    /**
     * Get the total distance travelled in the trip.
     * @param segs - list of location instances
     * @return dist - the distance travelled in km
     */
    private double getDist(@NonNull List<Segment> segs) {
        if (segs.size() < 1) {
            return 0;
        }
        double dist = 0;
        LocationData prev = segs.get(0).getLoc1();
        LocationData current;
        for (int i = 0; i < segs.size(); i++) {
            current = segs.get(i).getLoc2();
            dist += getPairDist(current, prev);
            prev = current;
        }
        return dist;
    }

    /**
     * Get the distance between a pair of location instances.
     * Uses the Haversine formula to get the Great Circle Distance.
     * Source: https://stackoverflow.com/questions/27928/calculate-distance-between-two-latitude-longitude-points-haversine-formula
     *
     * @param loc1 - the first location point
     * @param loc2 - the second location point
     * @return the distance between them in km
     */
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

    /**
     * Update the trip's map elements.
     * @param segments - the segments of the given trip
     * @param centerLat - the latitude of the center of the map
     * @param centerLon - the longitude of the center of the map
     * @param zoom - the zoom level of the map
     */
    public void setMap(List<Segment> segments, double centerLat, double centerLon, double zoom) {
        this.segments = segments;
        this.centerLat = centerLat;
        this.centerLon = centerLon;
        this.zoom = zoom;
    }

    // *************************** Getter Methods *****************************
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

    public List<Segment> getSegments() {
        return segments;
    }

    public double getCenterLat() {
        return centerLat;
    }

    public double getCenterLon() {
        return centerLon;
    }
}
