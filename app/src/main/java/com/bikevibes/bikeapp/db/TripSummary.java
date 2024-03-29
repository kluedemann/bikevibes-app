package com.bikevibes.bikeapp.db;

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
            dist += current.getDist(prev);
            prev = current;
        }
        return dist;
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

    /**
     * Calculate the bumpiness index from the RMS of vertical acceleration.
     * f transforms the score to a desired distribution.
     * MAX is the input that gives the maximum score
     * SCALE is the maximum score
     * @return - the bumpiness score to be displayed
     */
    public double getBumpScore() {
        final double MAX = 5.0;
        final double SCALE = 100;

        return Math.min(f(bumpiness) / f(MAX), 1) * SCALE;
    }

    /**
     * Transform the average RMS to obtain a bumpiness score.
     * Uses a log transformation to provide better distinction between low acceleration values.
     * k - a horizontal stretching constant
     * MIN - the highest value that maps to 0 bumpiness
     * @param x - the average RMS of vertical acceleration over the trip
     * @return - the transformed (but not normalized) bumpiness score
     */
    private double f(double x) {
        final double k = 2.0;
        final double MIN = 0.2;

        double newX = Math.max(0, x - MIN);
        return Math.log(k * newX + 1);
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
