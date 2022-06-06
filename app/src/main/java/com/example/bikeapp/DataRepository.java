package com.example.bikeapp;

import android.app.Application;

import androidx.lifecycle.LiveData;

import java.util.List;

public class DataRepository {
    private TrackingDao myDao;

    DataRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        myDao = db.myDao();
    }

    void insert(DataInstance dataInstance) {
        AppDatabase.databaseExecutor.execute(() -> {
            dataInstance.insert(myDao);
        });
    }

    void delete(DataInstance dataInstance) {
        AppDatabase.databaseExecutor.execute(() -> {
            dataInstance.delete(myDao);
        });
    }

    List<AccelerometerData> getAccel() {
        return myDao.getAccel();
    }

    List<LocationData> getLoc() {
        return myDao.getLocation();
    }
}
