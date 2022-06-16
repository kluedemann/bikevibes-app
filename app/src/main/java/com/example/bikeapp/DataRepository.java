package com.example.bikeapp;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.bikeapp.db.AccelerometerData;
import com.example.bikeapp.db.AppDatabase;
import com.example.bikeapp.db.DataInstance;
import com.example.bikeapp.db.LocationData;
import com.example.bikeapp.db.TrackingDao;

import java.util.List;

/**
 * Provides access to the database for app components.
 * Contains a singleton repository instance, data access object, and
 * some current data objects.
 */
public class DataRepository {
    private static volatile DataRepository instance;

    private final TrackingDao myDao;
    private final MutableLiveData<AccelerometerData> accel = new MutableLiveData<>();
    private final MutableLiveData<LocationData> location = new MutableLiveData<>();

    DataRepository(@NonNull final AppDatabase database) {
        myDao = database.myDao();
    }

    /**
     * Get the singleton repository instance or create it if needed.
     * @param database - the database that the repository accesses
     * @return instance - the repository instance
     */
    public static DataRepository getInstance(final AppDatabase database) {
        if (instance == null) {
            synchronized (DataRepository.class) {
                if (instance == null) {
                    instance = new DataRepository(database);
                }
            }
        }
        return instance;
    }

    void insert(DataInstance dataInstance) {
        AppDatabase.getExecutor().execute(() -> dataInstance.insert(myDao));
    }

    void delete(DataInstance dataInstance) {
        AppDatabase.getExecutor().execute(() -> dataInstance.delete(myDao));
    }

    void insertAccelBatch(List<AccelerometerData> accelList) {
        AppDatabase.getExecutor().execute(() -> myDao.insertAccelBatch(accelList));
    }

    void insertLocBatch(List<LocationData> locList) {
        AppDatabase.getExecutor().execute(() -> myDao.insertLocBatch(locList));
    }

    LiveData<AccelerometerData> getAccel() {
        return accel;
    }

    LiveData<LocationData> getLoc() {
        return location;
    }

    void setAccel(AccelerometerData acc) {
        accel.setValue(acc);
    }

    void setLoc(LocationData loc) {
        location.setValue(loc);
    }

    List<AccelerometerData> getAccelList() {
        return myDao.getAccel();
    }

    List<LocationData> getLocList() {
        return myDao.getLocation();
    }
}
