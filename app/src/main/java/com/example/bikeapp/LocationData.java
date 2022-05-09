package com.example.bikeapp;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class LocationData {
    @PrimaryKey
    public long timestamp;
    public double latitude;
    public double longitude;

    public LocationData(long timestamp, double latitude, double longitude) {
        this.timestamp = timestamp;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
