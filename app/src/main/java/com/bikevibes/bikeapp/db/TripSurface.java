package com.bikevibes.bikeapp.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.util.Locale;

@Entity
public class TripSurface extends DataInstance{
    private static final String URL_TEMPLATE = "http://162.246.157.171:8080/upload/surface?user_id=%s&trip_id=%d&surface=%s";

    @PrimaryKey
    private int tripID;
    private String surface;

    public TripSurface(int tripID, String surface) {
        this.tripID = tripID;
        this.surface = surface;
    }

    @Override
    public String getURL(String user_id) {
        return String.format(Locale.US, URL_TEMPLATE, user_id, tripID, surface);
    }

    @Override
    public void delete(@NonNull TrackingDao myDao) {
        myDao.deleteSurface(this);
    }

    @Override
    public void insert(@NonNull TrackingDao myDao) {
        myDao.insertTrip(this);
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
