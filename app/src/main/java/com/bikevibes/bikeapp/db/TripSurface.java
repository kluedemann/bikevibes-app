package com.bikevibes.bikeapp.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import org.json.JSONException;
import org.json.JSONObject;

@Entity
public class TripSurface extends DataInstance {

    @PrimaryKey
    private int tripID;
    private String surface;

    public TripSurface(int tripID, String surface) {
        this.tripID = tripID;
        this.surface = surface;
    }

    public JSONObject toJSON() {
        JSONObject jObject = new JSONObject();
        try {
            jObject.put("trip_id", tripID);
            jObject.put("surface", surface);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jObject;
    }

    @Override
    public void delete(@NonNull TrackingDao myDao) {
        myDao.deleteSurface(this);
    }

    @Override
    public void insert(@NonNull TrackingDao myDao) {
        myDao.insertSurface(this);
    }

    public int getTripID() {
        return tripID;
    }

    public void setTripID(int tripID) {
        this.tripID = tripID;
    }

    public String getSurface() {
        return surface;
    }

    public void setSurface(String surface) {
        this.surface = surface;
    }
}
