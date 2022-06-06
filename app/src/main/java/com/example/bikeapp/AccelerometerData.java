package com.example.bikeapp;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Locale;

@Entity
public class AccelerometerData extends DataInstance {
    @PrimaryKey
    public long timestamp;
    public float x;
    public float y;
    public float z;
    public int tripID;

    public AccelerometerData(long timestamp, float x, float y, float z, int tripID) {
        this.timestamp = timestamp;
        this.x = x;
        this.y = y;
        this.z = z;
        this.tripID = tripID;
    }

    public String getURL(String user_id) {
        String accel_temp = "http://162.246.157.171:8080/upload/accelerometer?user_id=%s&time_stamp=%d&trip_id=%d&x_accel=%f&y_accel=%f&z_accel=%f";
        return String.format(Locale.US, accel_temp, user_id, timestamp, tripID, x, y, z);
    }

    public int delete(TrackingDao myDao) {
        return myDao.deleteAccel(this);
    }

    public void insert(TrackingDao myDao) {
        myDao.insertAccel(this);
    }
}
