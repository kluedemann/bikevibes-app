package com.example.bikeapp.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Locale;

/**
 * Entity class for location objects.
 * Model for storing location data in local database.
 */
@Entity
public class LocationData extends DataInstance {
    private static final String URL_TEMPLATE = "http://162.246.157.171:8080/upload/location?user_id=%s&time_stamp=%d&trip_id=%d&latitude=%f&longitude=%f";

    @PrimaryKey
    public long timestamp;
    public double latitude;
    public double longitude;
    public int tripID;

    public LocationData(long timestamp, double latitude, double longitude, int tripID) {
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
        return String.format(Locale.US, URL_TEMPLATE, user_id, timestamp, tripID, latitude, longitude);
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
}
