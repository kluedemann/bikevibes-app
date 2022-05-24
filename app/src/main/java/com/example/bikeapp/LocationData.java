package com.example.bikeapp;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.util.Locale;

@Entity
public class LocationData extends DataInstance {
    @PrimaryKey
    public long timestamp;
    public double latitude;
    public double longitude;

    public LocationData(long timestamp, double latitude, double longitude) {
        this.timestamp = timestamp;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getURL(String user_id, int trip_id) {
        String location_temp = "http://162.246.157.171:8080/upload/location?user_id=%s&time_stamp=%d&trip_id=%d&latitude=%f&longitude=%f";
        return String.format(Locale.US, location_temp, user_id, timestamp, trip_id, latitude, longitude);
    }

    public int delete(MyDao myDao) {
        return myDao.deleteLocation(this);
    }
}
