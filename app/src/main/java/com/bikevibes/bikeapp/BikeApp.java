package com.bikevibes.bikeapp;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;
import com.bikevibes.bikeapp.db.AppDatabase;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;

import java.io.File;
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

        // Configure OSMdroid to use the cache folder rather than external storage
        IConfigurationProvider config = Configuration.getInstance();
        File basePath = new File(getCacheDir().getAbsolutePath(), "osmdroid");
        config.setOsmdroidBasePath(basePath);
        File tileCache = new File(config.getOsmdroidBasePath().getAbsolutePath(), "tile");
        config.setOsmdroidTileCache(tileCache);
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
            NotificationChannel channel = new NotificationChannel(getString(R.string.tracking_channel_id),
                    getString(R.string.tracking_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(getString(R.string.tracking_channel_desc));
            notificationManager.createNotificationChannel(channel);

            channel = new NotificationChannel(getString(R.string.upload_channel_id),
                    getString(R.string.upload_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(getString(R.string.upload_channel_desc));
            notificationManager.createNotificationChannel(channel);


        }
    }
}
