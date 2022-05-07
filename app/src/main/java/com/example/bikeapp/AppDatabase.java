package com.example.bikeapp;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {AccelerometerData.class, LocationData.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract MyDao myDao();
}
