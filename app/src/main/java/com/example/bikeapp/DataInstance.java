package com.example.bikeapp;

public abstract class DataInstance {

    public abstract String getURL(String user_id);
    public abstract int delete(TrackingDao myDao);
    public abstract void insert(TrackingDao myDao);
}