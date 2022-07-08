package com.bikevibes.bikeapp;

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
import com.bikevibes.bikeapp.db.AccelerometerData;
import com.bikevibes.bikeapp.db.DataInstance;
import com.bikevibes.bikeapp.db.LocationData;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Uploads data from the local database to the web server.
 * Started from the upload button in the MainActivity.
 */
public class UploadService extends Service {
    private static final String TAG = "UploadService";
    private static final String ACTION_UPLOAD = "com.bikevibes.bikeapp.UPLOAD";

    private DataRepository repository;
    private int numToUpload;
    private int numResponses;
    private int numUploaded;
    private RequestQueue queue;
    private boolean isUploading = false;
    private ExecutorService uploadExecutor;
    private String userID;
    private int tripID;

    /**
     * Required override method. This service cannot be bound to
     *
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
        getPrefs();
    }

    /**
     * Begin uploading data to the web server.
     *
     * @param intent  - the intent sent to the service
     * @param flags   - additional data about the start request
     * @param startID - can be used to distinguish service tasks
     * @return START_NOT_STICKY - indicates that the system should not recreate the service
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startID) {
        super.onStartCommand(intent, flags, startID);
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
        super.onDestroy();
        Log.d(TAG, "Destroyed!");
    }

    /**
     * Get the userID from the preferences file, or generate one.
     * IDs are 36 character long strings that are universally unique identifiers
     */
    private void getPrefs() {
        final String PREFS = getString(R.string.preference_file_key);
        final String USER_KEY = getString(R.string.prefs_user_key);
        final String TRIP_KEY = getString(R.string.prefs_trip_key);

        SharedPreferences sharedPref = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        userID = sharedPref.getString(USER_KEY, null);
        tripID = sharedPref.getInt(TRIP_KEY, 0);

        // Initialize user/trip ids on first opening of app
        if (userID == null) {
            userID = UUID.randomUUID().toString();
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString(USER_KEY, userID);
            editor.apply();

            final String url = String.format("http://162.246.157.171:8080/upload/alias?user_id=%s", userID);
            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, url, null, response -> {
                // onResponse: Called upon receiving a response from the server
            }, error -> {
                // onErrorResponse: Called upon receiving an error response
                Log.e(TAG, error.toString());
            });
            request.setTag(TAG);
            queue.add(request);
        }
    }

    /**
     * Query data from the local database and upload it to the server.
     */
    private void uploadData() {
        // Query data

        List<LocationData> locations = repository.getLocs(tripID);
        List<AccelerometerData> accel_readings = repository.getAccels(tripID);

        // Initialize counters
        numToUpload = locations.size() + accel_readings.size();
        numResponses = 0;
        numUploaded = 0;

        if (numToUpload == 0) {
            Intent intent = new Intent(ACTION_UPLOAD);
            intent.putExtra(getString(R.string.success_key), false);
            intent.putExtra(getString(R.string.message_key), getString(R.string.no_data_text));
            uploadCompleted(intent);
            return;
        }

        // Create notification and start in foreground
        final int NOTIFICATION_ID = 2;
        Notification notification = new NotificationCompat.Builder(getApplicationContext(), getString(R.string.upload_channel_id))
                .setSmallIcon(R.mipmap.ic_launcher) // notification icon
                .setContentTitle(getString(R.string.app_name)) // title for notification
                .setContentText(getString(R.string.upload_notification_text))// message for notification
                .setAutoCancel(true).build(); // clear notification after click
        startForeground(NOTIFICATION_ID, notification);

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

        //writePrefs();
    }

    /**
     * Upload a single data instance to the server using an HTTP POST request.
     * Uses a Volley request queue to handle requests and responses.
     * Define callbacks that are invoked upon receiving a response from the server.
     * Handle successful and unsuccessful responses.
     *
     * @param data (DataInstance) - the instance to upload
     */
    private void upload(@NonNull DataInstance data, String userID) {
        String url = data.getURL(userID);

        // Create the HTTP request
        JsonObjectRequest jORequest = new JsonObjectRequest(Request.Method.POST, url, null, response -> {
            // onResponse: Called upon receiving a response from the server
            //Log.d(TAG, String.format("SUCCESS: %s", isSuccess));
            boolean isSuccess = response.optBoolean(getString(R.string.HTTP_success_key), false);
            if (isSuccess) {
                repository.delete(data);
            }
            requestCompleted(isSuccess);
        }, error -> {
            // onErrorResponse: Called upon receiving an error response
            Log.e(TAG, error.toString());
            if (error instanceof TimeoutError) {
                // Sever could not be reached
                queue.cancelAll(TAG);

                // Send broadcast back
                Intent intent = new Intent(ACTION_UPLOAD);
                intent.putExtra(getString(R.string.success_key), false);
                intent.putExtra(getString(R.string.message_key), getString(R.string.timeout_text));
                uploadCompleted(intent);
            } else if (error instanceof ServerError && error.networkResponse.statusCode == 409) {
                // Discard local copy if server has duplicate data
                Log.d(TAG, "Duplicate!");
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
     *
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
            intent.putExtra(getString(R.string.success_key), true);
            intent.putExtra(getString(R.string.uploaded_key), numUploaded);
            intent.putExtra(getString(R.string.total_key), numResponses);
            uploadCompleted(intent);
        }
    }

    /**
     * Send a broadcast and stop the service once it is completed.
     * This may be called on an error, no data, or after all instances have been uploaded.
     *
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