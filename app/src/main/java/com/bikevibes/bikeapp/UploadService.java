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

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.TimeoutError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.bikevibes.bikeapp.db.AccelerometerData;
import com.bikevibes.bikeapp.db.DataInstance;
import com.bikevibes.bikeapp.db.LocationData;
import com.bikevibes.bikeapp.db.TripSurface;

import org.jetbrains.annotations.Contract;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
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
        }
    }

    /**
     * Query data from the local database and upload it to the server.
     */
    private void uploadData() {
        // Query data
        List<LocationData> locations = repository.getLocs(tripID);
        List<AccelerometerData> accel_readings = repository.getAccels(tripID);
        List<TripSurface> surfaces = repository.getSurfaces(tripID);

        // Initialize counters
        int numToUpload = locations.size() + accel_readings.size() + surfaces.size();

        // Return if no data to upload
        if (numToUpload == 0) {
            uploadCompleted(getString(R.string.no_data_text));
            return;
        }

        // Create notification and start in foreground
        final int NOTIFICATION_ID = 2;
        Notification notification = new NotificationCompat.Builder(getApplicationContext(), getString(R.string.upload_channel_id))
                .setSmallIcon(R.drawable.small_icon) // notification icon
                .setContentTitle(getString(R.string.app_name)) // title for notification
                .setContentText(getString(R.string.upload_notification_text))// message for notification
                .setAutoCancel(true).build(); // clear notification after click
        startForeground(NOTIFICATION_ID, notification);

        // Create the Volley request to send to the server
        final String url = "http://162.246.157.171:8080/upload";
        JSONObject data = getJSON(locations, accel_readings, surfaces);
        JsonObjectRequest jORequest = new JsonObjectRequest(Request.Method.POST, url, data, response -> {
            repository.deleteUpload(tripID);
            uploadCompleted(getString(R.string.upload_success));
        }, error -> {
            if (error instanceof TimeoutError) {
                uploadCompleted(getString(R.string.timeout_text));
            } else {
                uploadCompleted(getString(R.string.upload_error));
            }
        });

        // Set the retry policy and tag
        int INITIAL_TIMEOUT = Math.max(5000, numToUpload / 10);
        final int MAX_RETRIES = 0;
        jORequest.setRetryPolicy(new DefaultRetryPolicy(INITIAL_TIMEOUT, MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        jORequest.setTag(TAG);

        queue.add(jORequest);
    }

    /**
     * Create the JSON object to include in the POST request.
     * Contains the user ID and arrays of each data object to upload
     * @param locations - the list of LocationData to upload
     * @param accels - the list of AccelerometerData to upload
     * @param surfaces - the list of TripSurfaces to upload
     * @return - the JSON object to be sent to the server
     */
    @NonNull
    private JSONObject getJSON(List<LocationData> locations, List<AccelerometerData> accels, List<TripSurface> surfaces) {
        JSONObject data = new JSONObject();
        try {
            data.put("user_id", userID);
            data.put("accelerometer", getJsonArray(accels));
            data.put("locations", getJsonArray(locations));
            data.put("surfaces", getJsonArray(surfaces));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return data;
    }

    /**
     * Create a JSON array from a list of DataInstance objects.
     * @param data - list of DataInstance objects to convert to JSON
     * @return - the array of JSON objects representing the data
     */
    @NonNull
    @Contract("_ -> new")
    private JSONArray getJsonArray(@NonNull List<? extends DataInstance> data) {
        ArrayList<JSONObject> jsonData = new ArrayList<>();
        for (DataInstance instance : data) {
            jsonData.add(instance.toJSON());
        }
        return new JSONArray(jsonData);
    }

    /**
     * Send a broadcast to the MainActivity and stop the service once it is completed.
     * This may be called on an error, no data, or after all instances have been uploaded.
     * @param message (str) - the message to display in a Toast
     */
    private void uploadCompleted(String message) {
        // Create and send intent
        Intent intent = new Intent(ACTION_UPLOAD);
        intent.putExtra(getString(R.string.message_key), message);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);

        // Destroy UploadService
        isUploading = false;
        stopSelf();
    }

    public static String getAction() {
        return ACTION_UPLOAD;
    }
}