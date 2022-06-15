package com.example.bikeapp;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.example.bikeapp.db.AccelerometerData;
import com.example.bikeapp.db.DataInstance;
import com.example.bikeapp.db.LocationData;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Uploads data from the local database to the web server.
 * Started from the upload button in the MainActivity.
 */
public class UploadService extends Service {
    private static final String TAG = "UploadService";
    private static final String ACTION_UPLOAD = "com.example.bikeapp.UPLOAD";

    private DataRepository repository;
    private int numToUpload;
    private int numResponses;
    private int numUploaded;
    private RequestQueue queue;
    private boolean isUploading = false;
    private ExecutorService uploadExecutor;

    /**
     * Required override method. This service cannot be bound to
     * @param intent - the intent that is used to bind to the service
     * @return - a Binder which can be used to retrieve this service
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Initialize the service when it is first created.
     * Get the thread pool, request queue, and repository.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Created!");
        BikeApp app = (BikeApp) getApplication();
        repository = app.getRepository();

        uploadExecutor = app.getExecutors();

        queue = app.getQueue();
    }

    /**
     * Begin uploading data to the web server.
     * @param intent - the intent sent to the service
     * @param flags - additional data about the start request
     * @param startID - can be used to distinguish service tasks
     * @return START_NOT_STICKY - indicates that the system should not recreate the service
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startID) {
        Log.d(TAG, "Started!");
        if (!isUploading) {
            uploadExecutor.execute(this::uploadData);
            isUploading = true;
        }
        return START_NOT_STICKY;
    }

    /**
     * Clean up request queue and thread pool upon destruction.
     */
    @Override
    public void onDestroy() {
        Log.d(TAG, "Destroyed!");
    }

    /**
     * Get the userID from the preferences file, or generate one.
     * IDs are 36 character long strings that are universally unique identifiers
     * @return userID (str) - the user's unique ID
     */
    @NonNull
    private String getUserID() {
        String prefs = getString(R.string.preference_file_key);
        SharedPreferences sharedPref = getSharedPreferences(prefs, Context.MODE_PRIVATE);
        String userID = sharedPref.getString("user_id", null);

        // Initialize user/trip ids on first opening of app
        if (userID == null) {
            userID = UUID.randomUUID().toString();
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("user_id", userID);
            editor.apply();
        }
        return userID;
    }

    /**
     * Query data from the local database and upload it to the server.
     */
    private void uploadData() {
        // Query data
        List<LocationData> locations = repository.getLocList();
        List<AccelerometerData> accel_readings = repository.getAccelList();

        // Initialize counters
        numToUpload = locations.size() + accel_readings.size();
        numResponses = 0;
        numUploaded = 0;

        if (numToUpload == 0) {
            Intent intent = new Intent(ACTION_UPLOAD);
            intent.putExtra("success", false);
            intent.putExtra("message", "No Data");
            uploadCompleted(intent);
            return;
        }

        // Create notification and start in foreground
        Notification notification = new NotificationCompat.Builder(getApplicationContext(), "2")
                .setSmallIcon(R.mipmap.ic_launcher) // notification icon
                .setContentTitle("BikeApp") // title for notification
                .setContentText("Uploading data")// message for notification
                .setAutoCancel(true).build(); // clear notification after click
        startForeground(2, notification);

        String userID = getUserID();

        // Upload location data
        for (int i = 0; i < locations.size(); i++) {
            LocationData loc = locations.get(i);
            upload(loc, userID);
            //Log.d(TAG, String.format("LOCATION: %f, %f, %d", loc.latitude, loc.longitude, loc.timestamp));
        }

        // Upload accelerometer data
        for (int i = 0; i < accel_readings.size(); i++) {
            AccelerometerData acc = accel_readings.get(i);
            upload(acc, userID);
            //Log.d(TAG, String.format("ACCEL: %f, %f, %f, %d", acc.x, acc.y, acc.z, acc.timestamp));
        }
    }

    /**
     * Upload a single data instance to the server using an HTTP POST request.
     * Uses a Volley request queue to handle requests and responses.
     * Define callbacks that are invoked upon receiving a response from the server.
     * Handle successful and unsuccessful responses.
     * @param data (DataInstance) - the instance to upload
     */
    private void upload(@NonNull DataInstance data, String userID) {
        String url = data.getURL(userID);

        // Create the HTTP request
        JsonObjectRequest jORequest = new JsonObjectRequest(Request.Method.POST, url, null, response -> {
            // onResponse: Called upon receiving a response from the server
            //Log.d(TAG, String.format("SUCCESS: %s", isSuccess));
            boolean isSuccess = response.optBoolean("success", false);
            requestCompleted(isSuccess);
            if (isSuccess) {
                repository.delete(data);
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
                uploadCompleted(intent);
            } else if (error instanceof ServerError && error.networkResponse.statusCode == 500) {
                // Discard local copy if server has duplicate data
                repository.delete(data);
            }
            requestCompleted(false);
        });

        // Add request to the queue
        jORequest.setTag(TAG);
        queue.add(jORequest);
    }

    /**
     * Keep track of how many requests are completed successfully.
     * Called upon receiving a response or error from an HTTP request.
     * Send a broadcast to the MainActivity once all responses have been received
     * @param success (boolean) - true if the response was a successful one
     */
    private void requestCompleted(boolean success) {
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
            uploadCompleted(intent);
        }
    }

    /**
     * Send a broadcast and stop the service once it is completed.
     * This may be called on an error, no data, or after all instances have been uploaded.
     * @param intent (Intent) - the message to return to the MainActivity
     *               success - (boolean) whether upload completed successfully
     *               total - (int) number of rows attempted to upload
     *               uploaded - (int) number of rows uploaded successfully
     *               message - (String) message to display if unsuccessful
     */
    private void uploadCompleted(Intent intent) {
        Log.d(TAG, "Stopped!");
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
        isUploading = false;
        queue.cancelAll(TAG);
        stopSelf();
    }

    public static String getAction() {
        return ACTION_UPLOAD;
    }
}