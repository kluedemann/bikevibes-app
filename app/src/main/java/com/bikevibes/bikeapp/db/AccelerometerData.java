package com.bikevibes.bikeapp.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Date;
import java.util.Locale;

/**
 * Entity class for accelerometer readings.
 * Model for storing accelerometer data in the local database.
 */
@Entity
public class AccelerometerData extends DataInstance {
    private static final String URL_TEMPLATE = "http://162.246.157.171:8080/upload/accelerometer?user_id=%s&time_stamp=%d&trip_id=%d&x_accel=%f&y_accel=%f&z_accel=%f";

    @PrimaryKey
    @NonNull
    private Date timestamp;
    private float x;
    private float y;
    private float z;
    private int tripID;

    public AccelerometerData(@NonNull Date timestamp, float x, float y, float z, int tripID) {
        this.timestamp = timestamp;
        this.x = x;
        this.y = y;
        this.z = z;
        this.tripID = tripID;
    }

    public AccelerometerData(@NonNull Date timestamp, @NonNull float[] values, int tripID) {
        this.timestamp = timestamp;
        this.x = values[0];
        this.y = values[1];
        this.z = values[2];
        this.tripID = tripID;
    }

    /**
     * Generate the URL used to upload this data instance.
     * @param user_id - the userID used to upload it
     * @return the URL string to be sent in the HTTP request
     */
    @Override
    public String getURL(String user_id) {
        return String.format(Locale.US, URL_TEMPLATE, user_id, timestamp.getTime(), tripID, x, y, z);
    }

    /**
     * Delete this object from the database
     * @param myDao - the Data access object
     */
    @Override
    public void delete(@NonNull TrackingDao myDao) {
        myDao.deleteAccel(this);
    }

    /**
     * Insert this object into the database
     * @param myDao - the data access object
     */
    @Override
    public void insert(@NonNull TrackingDao myDao) {
        myDao.insertAccel(this);
    }

    public Date getTimestamp() {return timestamp;}
    public long getTime() {
        return timestamp.getTime();
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getZ() {
        return z;
    }

    public int getTripID() {
        return tripID;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public void setX(float x) {
        this.x = x;
    }

    public void setY(float y) {
        this.y = y;
    }

    public void setZ(float z) {
        this.z = z;
    }

    public void setTripID(int tripID) {
        this.tripID = tripID;
    }
}