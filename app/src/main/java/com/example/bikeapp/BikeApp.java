package com.example.bikeapp;

import android.app.Application;

import com.example.bikeapp.db.AppDatabase;

public class BikeApp extends Application {

    public AppDatabase getDatabase() {
        return AppDatabase.getDatabase(this);
    }

    public DataRepository getRepository() {
        return DataRepository.getInstance(getDatabase());
    }
}
