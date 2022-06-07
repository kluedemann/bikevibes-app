package com.example.bikeapp;

import android.app.Application;

public class BikeApp extends Application {

    public AppDatabase getDatabase() {
        return AppDatabase.getDatabase(this);
    }

    public DataRepository getRepository() {
        return DataRepository.getInstance(getDatabase());
    }
}
