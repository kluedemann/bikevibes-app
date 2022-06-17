package com.example.bikeapp;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.bikeapp.db.AccelerometerData;
import com.example.bikeapp.db.AppDatabase;
import com.example.bikeapp.db.LocationData;

import java.util.Date;

/**
 * Contains the business logic for the UI.
 * Retrieves data from the repository and provides it to the MainActivity.
 */
public class TrackingViewModel extends AndroidViewModel {

    private final DataRepository mRepository;
    private int tripID;

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

    LiveData<AccelerometerData> getAccel() {
        return mRepository.getAccel();
    }

    LiveData<LocationData> getLoc() {
        return mRepository.getLoc();
    }

    void update() {
        if (tripID != 0) {
            mRepository.update(tripID);
        }
    }

    private void getTripID() {
        Application app = getApplication();
        String PREFS = app.getString(R.string.preference_file_key);
        SharedPreferences sharedPref = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        tripID = sharedPref.getInt(app.getString(R.string.prefs_trip_key), 0);
    }


}
