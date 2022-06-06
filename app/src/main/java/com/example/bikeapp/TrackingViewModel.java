package com.example.bikeapp;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;

public class TrackingViewModel extends AndroidViewModel {

    private DataRepository mRepository;

    private final MutableLiveData<AccelerometerData> mAccel;
    private final MutableLiveData<LocationData> mLoc;
    private boolean isTracking = false;

    public TrackingViewModel(Application application) {
        super(application);
        mRepository = new DataRepository(application);
        mAccel = new MutableLiveData<>();
        mLoc = new MutableLiveData<>();
    }

    public void setAccel(AccelerometerData acc) {
        mAccel.setValue(acc);
        if (isTracking) {
            insert(acc);
        }
    }

    public void setLoc(LocationData loc) {
        mLoc.setValue(loc);
        if (isTracking) {
            insert(loc);
        }
    }

    LiveData<AccelerometerData> getAccel() {
        return mAccel;
    }

    LiveData<LocationData> getLoc() {
        return mLoc;
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
        return mRepository.getAccel();
    }

    List<LocationData> getAllLoc() {
        return mRepository.getLoc();
    }


}
