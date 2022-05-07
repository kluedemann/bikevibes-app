package com.example.bikeapp;

import androidx.room.Dao;
import androidx.room.Insert;

@Dao
public interface MyDao {
    @Insert
    void insertLocation(LocationData loc);

    @Insert
    void insertAccel(AccelerometerData acc);
}
