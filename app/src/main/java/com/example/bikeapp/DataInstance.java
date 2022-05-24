package com.example.bikeapp;

public abstract class DataInstance {

    public abstract String getURL(String user_id);
    public abstract int delete(MyDao myDao);
}