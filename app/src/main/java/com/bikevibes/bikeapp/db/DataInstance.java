package com.bikevibes.bikeapp.db;

import org.json.JSONObject;

/**
 * Abstract class for data objects that need to interact with the database.
 */
public abstract class DataInstance {

    public abstract void delete(TrackingDao myDao);
    public abstract void insert(TrackingDao myDao);
    public abstract JSONObject toJSON();
}