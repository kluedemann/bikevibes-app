package com.example.bikeapp;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.bikeapp.db.AccelerometerData;
import com.example.bikeapp.db.AppDatabase;
import com.example.bikeapp.db.DataInstance;
import com.example.bikeapp.db.LocationData;
import com.example.bikeapp.db.TrackingDao;

import java.util.List;

public class DataRepository {
    private static DataRepository sInstance;

    private final TrackingDao myDao;
    private final MutableLiveData<AccelerometerData> mAccel = new MutableLiveData<>();
    private final MutableLiveData<LocationData> mLocation = new MutableLiveData<>();

    DataRepository(final AppDatabase database) {
        myDao = database.myDao();
    }

    public static DataRepository getInstance(final AppDatabase database) {
        if (sInstance == null) {
            synchronized (DataRepository.class) {
                if (sInstance == null) {
                    sInstance = new DataRepository(database);
                }
            }
        }
        return sInstance;
    }

    void insert(DataInstance dataInstance) {
        AppDatabase.getExecutor().execute(() -> dataInstance.insert(myDao));
    }

    void delete(DataInstance dataInstance) {
        AppDatabase.getExecutor().execute(() -> dataInstance.delete(myDao));
    }

    LiveData<AccelerometerData> getAccel() {
        return mAccel;
    }

    LiveData<LocationData> getLoc() {
        return mLocation;
    }

    void setAccel(AccelerometerData acc) {
        mAccel.setValue(acc);
    }

    void setLoc(LocationData loc) {
        mLocation.setValue(loc);
    }

    List<AccelerometerData> getAccelList() {
        return myDao.getAccel();
    }

    List<LocationData> getLocList() {
        return myDao.getLocation();
    }
}
