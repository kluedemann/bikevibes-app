package com.bikevibes.bikeapp.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Locale;

/**
 * Entity class for accelerometer readings.
 * Model for storing accelerometer data in the local database.
 */
@Entity
public class AccelerometerData extends DataInstance {

    @PrimaryKey
    @NonNull
    private Date timestamp;
    private Float x;
    private Float y;
    private Float z;
    private int tripID;

    /**
     * Default constructor for Room to use.
     * @param timestamp - the timestamp of the sensor reading
     * @param x - the x-coordinate of acceleration
     * @param y - the y-coordinate of acceleration
     * @param z - the z-coordinate (vertical) acceleration
     * @param tripID - the trip ID of the record
     */
    public AccelerometerData(@NonNull Date timestamp, Float x, Float y, Float z, int tripID) {
        this.timestamp = timestamp;
        this.x = x;
        this.y = y;
        this.z = z;
        this.tripID = tripID;
    }

    /**
     * Initialize the AccelerometerData using a vector.
     * @param timestamp - the timestamp of the sensor reading
     * @param values - the acceleration vector
     * @param tripID - the trip ID of the record
     */
    public AccelerometerData(@NonNull Date timestamp, @NonNull float[] values, int tripID) {
        this.timestamp = timestamp;
        this.x = values[0];
        this.y = values[1];
        this.z = values[2];
        this.tripID = tripID;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.getDefault(),"Timestamp: %d, Trip: %d, X: %f, Y: %f, Z: %f", timestamp.getTime(), tripID, x, y, z);
    }

    /**
     * Create a JSON object representing the AccelerometerData
     * @return - a JSON object that can be uploaded
     */
    public JSONObject toJSON() {
        JSONObject jObject = new JSONObject();
        try {
            jObject.put("time_stamp", timestamp.getTime());
            jObject.put("x_accel", x);
            jObject.put("y_accel", y);
            jObject.put("z_accel", z);
            jObject.put("trip_id", tripID);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jObject;
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

    // ***************************** Getters and Setters *******************************************

    @NonNull
    public Date getTimestamp() {return timestamp;}

    public Float getX() {
        return x;
    }

    public Float getY() {
        return y;
    }

    public Float getZ() {
        return z;
    }

    public int getTripID() {
        return tripID;
    }

    public void setTimestamp(@NonNull Date timestamp) {
        this.timestamp = timestamp;
    }

    public void setX(Float x) {
        this.x = x;
    }

    public void setY(Float y) {
        this.y = y;
    }

    public void setZ(Float z) {
        this.z = z;
    }

    public void setTripID(int tripID) {
        this.tripID = tripID;
    }
}
