package com.example.bikeapp;

import android.hardware.SensorEvent;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class AccelerometerData {
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

    public AccelerometerData(SensorEvent event) {
        timestamp = event.timestamp;
        x = event.values[0];
        y = event.values[1];
        z = event.values[2];
    }


}
