package com.bikevibes.bikeapp.db;

/**
 * Abstract class for data objects that need to interact with the database.
 */
public abstract class DataInstance {

    public abstract String getURL(String user_id);
    public abstract void delete(TrackingDao myDao);
    public abstract void insert(TrackingDao myDao);
}