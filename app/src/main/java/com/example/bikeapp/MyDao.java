package com.example.bikeapp;

import android.location.Location;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertLocation(LocationData loc);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAccel(AccelerometerData acc);

    @Query("DELETE FROM AccelerometerData")
    int clearAccel();

    @Query("DELETE FROM LocationData")
    int clearLocation();

    @Query("SELECT * FROM LocationData")
    List<LocationData> getLocation();

    @Query("SELECT * FROM AccelerometerData")
    List<AccelerometerData> getAccel();

    @Delete
    int deleteLocation(LocationData loc);

    @Delete
    int deleteAccel(AccelerometerData acc);
}
