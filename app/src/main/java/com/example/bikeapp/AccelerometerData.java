package com.example.bikeapp;

import android.hardware.SensorEvent;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.Locale;

@Entity
public class AccelerometerData extends DataInstance {
    @PrimaryKey
    public long timestamp;
    public float x;
    public float y;
    public float z;

    public AccelerometerData(long timestamp, float x, float y, float z) {
        this.timestamp = timestamp;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String getURL(String user_id, int trip_id) {
        String accel_temp = "http://162.246.157.171:8080/upload/accelerometer?user_id=%s&time_stamp=%d&trip_id=%d&x_accel=%f&y_accel=%f&z_accel=%f";
        return String.format(Locale.US, accel_temp, user_id, timestamp, trip_id, x, y, z);
    }

    public int delete(MyDao myDao) {
        return myDao.deleteAccel(this);
    }
}
