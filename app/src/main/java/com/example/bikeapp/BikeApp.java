package com.example.bikeapp;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.example.bikeapp.db.AppDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Application subclass that provides access to shared resources.
 * Initializes and allows components to access the thread pool, request queue,
 * database, and repository.
 */
public class BikeApp extends Application {

    private static final int THREADS = 1;
    private static final ExecutorService executors = Executors.newFixedThreadPool(THREADS);
    private static volatile RequestQueue queue;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }

    public ExecutorService getExecutors() {
        return executors;
    }

    public AppDatabase getDatabase() {
        return AppDatabase.getDatabase(this);
    }

    public DataRepository getRepository() {
        return DataRepository.getInstance(getDatabase());
    }

    /**
     * Get the singleton request queue or create it.
     * @return - queue - the Volley RequestQueue for handling HTTP requests
     */
    public RequestQueue getQueue() {
        if (queue == null) {
            synchronized (this) {
                if (queue == null) {
                    queue = Volley.newRequestQueue(this);
                }
            }
        }
        return queue;
    }

    /**
     * Create notification channels upon app startup.
     * This has no effect if channels have already been created.
     */
    private void createNotificationChannels() {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            // Create upload notification channel if needed
            // Create tracking notification channel
            NotificationChannel channel = new NotificationChannel("1",
                    "Tracking",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Indicates that the app is currently tracking your GPS location and accelerometer data. Disable the tracking switch in the app to stop.");
            notificationManager.createNotificationChannel(channel);

            channel = new NotificationChannel("2",
                    "Upload",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Appears when data is being uploaded to the web server.");
            notificationManager.createNotificationChannel(channel);


        }
    }
}
