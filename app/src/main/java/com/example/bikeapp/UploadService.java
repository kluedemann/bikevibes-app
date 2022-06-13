package com.example.bikeapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.bikeapp.db.AccelerometerData;
import com.example.bikeapp.db.DataInstance;
import com.example.bikeapp.db.LocationData;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UploadService extends Service {

    private static final String ACTION_UPLOAD = "com.example.bikeapp.UPLOAD";
    private static final String TAG = "UploadService";
    private DataRepository mRepository;
    private int numToUpload;
    private int numResponses;
    private int numUploaded;
    private RequestQueue queue;
    private String userID;
    private boolean isUploading = false;
    private ExecutorService uploadExecutor;

    public class LocalBinder extends Binder {
        UploadService getService() {
            return UploadService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        BikeApp app = (BikeApp) getApplication();
        mRepository = app.getRepository();

        uploadExecutor = Executors.newFixedThreadPool(1);

        queue = Volley.newRequestQueue(this);

        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("2",
                    "Upload",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Appears when data is being uploaded to the web server.");
            mNotificationManager.createNotificationChannel(channel);
        }

        getUserID();
    }

    private void getUserID() {
        String PREFS = "com.example.bikeapp.TRACKING_INFO";
        SharedPreferences sharedPref = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        userID = sharedPref.getString("user_id", null);

        // Initialize user/trip ids on first opening of app
        if (userID == null) {
            userID = UUID.randomUUID().toString();
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("user_id", userID);
            editor.apply();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startID) {
        Log.d(TAG, "Started!");
        if (!isUploading) {
            uploadData();
            isUploading = true;
        }
        return START_NOT_STICKY;
    }

    private void uploadData() {
        // Query data from the local database and upload it to the server
        uploadExecutor.execute(() -> {
            // Query data
            List<LocationData> locations = mRepository.getLocList();
            List<AccelerometerData> accel_readings = mRepository.getAccelList();

            // Initialize counters
            numToUpload = locations.size() + accel_readings.size();
            numResponses = 0;
            numUploaded = 0;

            if (numToUpload == 0) {
                Intent intent = new Intent(ACTION_UPLOAD);
                intent.putExtra("success", false);
                intent.putExtra("message", "No Data");
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                isUploading = false;
                Log.d(TAG, "Stopped!");
                stopSelf();
            } else {
                Notification notification = new NotificationCompat.Builder(getApplicationContext(), "1")
                        .setSmallIcon(R.mipmap.ic_launcher) // notification icon
                        .setContentTitle("BikeApp") // title for notification
                        .setContentText("Uploading data")// message for notification
                        .setAutoCancel(true).build(); // clear notification after click
                startForeground(1, notification);
            }

            // Upload location data
            for (int i = 0; i < locations.size(); i++) {
                LocationData loc = locations.get(i);
                upload(loc);
                //Log.d(TAG, String.format("LOCATION: %f, %f, %d", loc.latitude, loc.longitude, loc.timestamp));
            }

            // Upload accelerometer data
            for (int i = 0; i < accel_readings.size(); i++) {
                AccelerometerData acc = accel_readings.get(i);
                upload(acc);
                //Log.d(TAG, String.format("ACCEL: %f, %f, %f, %d", acc.x, acc.y, acc.z, acc.timestamp));
            }
        });
    }

    private void upload(DataInstance data) {
        // Send an HTTP request containing the data instance to the server
        // Adds a request to the Volley request queue

        String url = data.getURL(userID);

        // Create the HTTP request
        JsonObjectRequest jORequest = new JsonObjectRequest(Request.Method.POST, url, null, response -> {
            // onResponse: Called upon receiving a response from the server
            //Log.d(TAG, String.format("SUCCESS: %s", isSuccess));
            boolean isSuccess = response.optBoolean("success", false);
            requestCompleted(isSuccess);
            if (isSuccess) {
                mRepository.delete(data);
            }
        }, error -> {
            // onErrorResponse: Called upon receiving an error response
            Log.e(TAG, error.toString());
            if (error instanceof TimeoutError) {
                // Sever could not be reached
                queue.cancelAll(TAG);

                // Send broadcast back
                Intent intent = new Intent(ACTION_UPLOAD);
                intent.putExtra("success", false);
                intent.putExtra("message", "Connection timed out");
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                isUploading = false;
                Log.d(TAG, "Stopped!");
                stopSelf();
            } else if (error instanceof ServerError && error.networkResponse.statusCode == 500) {
                // Discard local copy if server has duplicate data
                mRepository.delete(data);
            }
            requestCompleted(false);
        });

        // Add request to the queue
        jORequest.setTag(TAG);
        queue.add(jORequest);
    }

    private void requestCompleted(boolean success) {
        // Called upon receiving a response or error from an HTTP request
        // success argument indicates whether the data was successfully uploaded
        // Keep track of how many requests are completed and display success message

        // Increment response counters
        if (success) {
            numUploaded++;
        }
        numResponses++;

        // Display confirmation upon receiving final response
        if (numResponses == numToUpload) {
            Log.d(TAG, String.format("SUCCESSES: %d, TOTAL: %d", numUploaded, numResponses));

            // Send broadcast
            Intent intent = new Intent(ACTION_UPLOAD);
            intent.putExtra("success", true);
            intent.putExtra("uploaded", numUploaded);
            intent.putExtra("total", numResponses);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            isUploading = false;
            Log.d(TAG, "Stopped!");
            stopSelf();
        }
    }

    public static String getAction() {
        return ACTION_UPLOAD;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Destroyed!");
        queue.cancelAll(TAG);
        uploadExecutor.shutdown();
    }
}