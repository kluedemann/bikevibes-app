package com.bikevibes.bikeapp.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import org.osmdroid.util.GeoPoint;

import java.util.Date;
import java.util.Locale;

/**
 * Entity class for location objects.
 * Model for storing location data in local database.
 */
@Entity
public class LocationData extends DataInstance {
    private static final String URL_TEMPLATE = "http://162.246.157.171:8080/upload/location?user_id=%s&time_stamp=%d&trip_id=%d&latitude=%f&longitude=%f";
    private static final int RADIUS = 6371;

    @PrimaryKey
    @NonNull
    private Date timestamp;
    private Double latitude;
    private Double longitude;
    private int tripID;

    public LocationData(@NonNull Date timestamp, Double latitude, Double longitude, int tripID) {
        this.timestamp = timestamp;
        this.latitude = latitude;
        this.longitude = longitude;
        this.tripID = tripID;
    }

    /**
     * Generate the URL used to upload this data instance
     * @param user_id - the userID used to upload it
     * @return the URL string to be sent in the HTTP request
     */
    @Override
    public String getURL(String user_id) {
        return String.format(Locale.US, URL_TEMPLATE, user_id, timestamp.getTime(), tripID, latitude, longitude);
    }

    /**
     * Delete this object from the database
     * @param myDao - the Data Access Object
     */
    @Override
    public void delete(@NonNull TrackingDao myDao) {
        myDao.deleteLocation(this);
    }

    /**
     * Insert this object into the database.
     * @param myDao - the Data Access object
     */
    @Override
    public void insert(@NonNull TrackingDao myDao) {
        myDao.insertLocation(this);
    }

    /**
     * Get the distance between a pair of location instances.
     * Uses the Haversine formula to get the Great Circle Distance.
     * Source: https://stackoverflow.com/questions/27928/calculate-distance-between-two-latitude-longitude-points-haversine-formula
     *
     * @param loc - the location to calculate distance from
     * @return the distance between them in km
     */
    public double getDist(@NonNull LocationData loc) {
        double dLat = Math.toRadians(this.getLatitude() - loc.getLatitude());
        double dLon = Math.toRadians(this.getLongitude() - loc.getLongitude());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.sin(dLon / 2) * Math.sin(dLon / 2)
                * Math.cos(Math.toRadians(this.getLatitude())) * Math.cos(Math.toRadians(loc.getLatitude()));
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        //Log.d("Distance", String.format(Locale.getDefault(), "%f, %f, %f", dLat, dLon, r*c));
        return RADIUS * c;
    }

    @Override
    @NonNull
    public String toString() {
        return String.format(Locale.getDefault(),"%d, %f, %f, %d", timestamp.getTime(), latitude, longitude, tripID);
    }

    // ******************************** Getter Methods ********************************************

    @NonNull
    public Date getTimestamp() {
        return timestamp;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public int getTripID() {
        return tripID;
    }

    public void setTimestamp(@NonNull Date timestamp) {
        this.timestamp = timestamp;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public void setTripID(int tripID) {
        this.tripID = tripID;
    }

    public GeoPoint getGeoPoint() {
        return new GeoPoint(latitude, longitude);
    }
}
