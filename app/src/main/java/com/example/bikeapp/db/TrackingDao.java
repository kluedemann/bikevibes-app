package com.example.bikeapp.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * The Data Access Object for the Room database.
 * Provides methods for interacting with the database.
 * Uses annotations to automatically generate the code for each method.
 */
@Dao
public interface TrackingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertLocation(LocationData loc);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAccel(AccelerometerData acc);

    @Query("SELECT * FROM LocationData")
    List<LocationData> getLocation();

    @Query("SELECT * FROM AccelerometerData")
    List<AccelerometerData> getAccel();

    @Delete
    void deleteLocation(LocationData loc);

    @Delete
    void deleteAccel(AccelerometerData acc);
}
