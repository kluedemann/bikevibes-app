package com.example.bikeapp;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class LocationData {
    @PrimaryKey
    public long timestamp;
    public double latitude;
    public double longitude;
}
