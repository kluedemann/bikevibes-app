package com.example.bikeapp;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.bikeapp.db.AccelerometerData;
import com.example.bikeapp.db.LocationData;


public class TrackingViewModel extends AndroidViewModel {

    private final DataRepository mRepository;

    public TrackingViewModel(Application application) {
        super(application);
        BikeApp app = (BikeApp) application;
        mRepository = app.getRepository();
    }

    LiveData<AccelerometerData> getAccel() {
        return mRepository.getAccel();
    }

    LiveData<LocationData> getLoc() {
        return mRepository.getLoc();
    }
}
