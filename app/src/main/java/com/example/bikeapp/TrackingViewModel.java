package com.example.bikeapp;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polyline;

import java.util.Date;
import java.util.List;

/**
 * Contains the business logic for the UI.
 * Retrieves data from the repository and provides it to the MainActivity.
 */
public class TrackingViewModel extends AndroidViewModel {

    private final DataRepository mRepository;
    private int tripID;
    private int maxID;

    public TrackingViewModel(Application application) {
        super(application);
        BikeApp app = (BikeApp) application;
        mRepository = app.getRepository();
        getTripID();
    }

    LiveData<Date> getStart() {
        return mRepository.getStart();
    }

    LiveData<Date> getEnd() {
        return mRepository.getEnd();
    }

    LiveData<Double> getDist() {
        return mRepository.getDist();
    }

    LiveData<Double> getSpeed() {
        return mRepository.getSpeed();
    }

    LiveData<Double> getBumpiness() {
        return mRepository.getBumpiness();
    }

    LiveData<Double> getZoom() {
        return mRepository.getZoom();
    }

    LiveData<GeoPoint> getCenter() {
        return mRepository.getCenter();
    }

    LiveData<List<Polyline>> getLines() {
        return mRepository.getLines();
    }

    void update() {
        if (tripID != 0) {
            mRepository.update(tripID);
        }
    }

    boolean decrement() {
        if (tripID > 1) {
            tripID--;
            update();
            return true;
        }
        return false;
    }

    boolean increment() {
        if (tripID < maxID) {
            tripID++;
            update();
            return true;
        }
        return false;
    }

    void incrementMax() {
        maxID++;
    }

    private void getTripID() {
        Application app = getApplication();
        String PREFS = app.getString(R.string.preference_file_key);
        SharedPreferences sharedPref = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        tripID = sharedPref.getInt(app.getString(R.string.prefs_trip_key), 0);
        maxID = tripID;
    }


}
