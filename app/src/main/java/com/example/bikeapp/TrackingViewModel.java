package com.example.bikeapp;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

public class TrackingViewModel extends AndroidViewModel {

    private final DataRepository mRepository;
    private boolean isTracking = false;

    public TrackingViewModel(Application application) {
        super(application);
        BikeApp app = (BikeApp) application;
        mRepository = app.getRepository();
    }

    public void setAccel(AccelerometerData acc) {
        mRepository.setAccel(acc);
        if (isTracking) {
            insert(acc);
        }
    }

    public void setLoc(LocationData loc) {
        mRepository.setLoc(loc);
        if (isTracking) {
            insert(loc);
        }
    }

    LiveData<AccelerometerData> getAccel() {
        return mRepository.getAccel();
    }

    LiveData<LocationData> getLoc() {
        return mRepository.getLoc();
    }

    public void insert(DataInstance dataInstance) {
        mRepository.insert(dataInstance);
    }

    public void delete(DataInstance dataInstance) {
        mRepository.delete(dataInstance);
    }

    boolean getTracking() {
        return isTracking;
    }

    public void setTracking(boolean tracking) {
        isTracking = tracking;
    }

    List<AccelerometerData> getAllAccel() {
        return mRepository.getAccelList();
    }

    List<LocationData> getAllLoc() {
        return mRepository.getLocList();
    }


}
