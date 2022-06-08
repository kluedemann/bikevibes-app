package com.example.bikeapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModelProvider;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private TextView[] dataViews;
    private TextView[] locationViews;
    private TrackingViewModel mViewModel;
    private ExecutorService executorService;
    private int numUploaded;
    private int numResponses;
    private int numToUpload;
    private Button uploadButton;
    private RequestQueue queue;
    private String userID;
    private TrackingService mService;
    private boolean mBound;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            TrackingService.LocalBinder binder = (TrackingService.LocalBinder) iBinder;
            mService = binder.getService();
            mBound = true;

            SwitchCompat mySwitch = findViewById(R.id.tracking_switch);
            mySwitch.setChecked(mService.getTracking());
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mViewModel = new ViewModelProvider(this).get(TrackingViewModel.class);
        mViewModel.getLoc().observe(this, this::setLocationText);
        mViewModel.getAccel().observe(this, this::setAccelText);



        // Get user/trip ids
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        userID = sharedPref.getString("user_id", null);

        // Initialize user/trip ids on first opening of app
        if (userID == null) {
            userID = UUID.randomUUID().toString();
            //userID = "test";
            writePrefs();
        }

        // Collect Text boxes to display data
        dataViews = new TextView[3];
        dataViews[0] = findViewById(R.id.x_accel_text);
        dataViews[1] = findViewById(R.id.y_accel_text);
        dataViews[2] = findViewById(R.id.z_accel_text);
        locationViews = new TextView[2];
        locationViews[0] = findViewById(R.id.latitude_data);
        locationViews[1] = findViewById(R.id.longitude_data);

        // Enable location tracking
        // Check if permissions have been granted, request them if not
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: ASSUMES PERMISSIONS ARE ALWAYS ACCEPTED
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        // Create background thread to handle DB operations
        executorService = Executors.newFixedThreadPool(1);

        // Handle Tracking switch
        SwitchCompat mySwitch = findViewById(R.id.tracking_switch);
        mySwitch.setOnCheckedChangeListener((compoundButton, b) -> {

            if (b && !mService.getTracking()) {
                Intent intent = new Intent(getApplicationContext(), TrackingService.class);
                getApplicationContext().startService(intent);
            } else if (!b && mService.getTracking()){
                mService.disableTracking();
            }
        });

        // Initialize HTTP request queue
        queue = Volley.newRequestQueue(this);

        // Handle Upload Button
        uploadButton = findViewById(R.id.upload_button);
        uploadButton.setOnClickListener(view -> {
            // Get data from local DB and upload to server
            uploadButton.setEnabled(false);
            executorService.execute(() -> {
                uploadData();

                // Display "No data" if nothing to upload
                if (numToUpload == 0) {
                    runOnUiThread(() -> {
                        // Display success message
                        uploadButton.setEnabled(true);
                        Toast.makeText(MainActivity.this, "No data", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });
    }

    protected void onResume() {
        super.onResume();

        if (!mBound) {
            Intent intent = new Intent(getApplicationContext(), TrackingService.class);
            getApplicationContext().bindService(intent, connection, Context.BIND_AUTO_CREATE);
        }

        uploadButton.setEnabled(true);
    }

    protected void onPause() {
        // Stop all background processes upon app being paused
        super.onPause();
        if (mBound) {
            getApplicationContext().unbindService(connection);
            mBound = false;
        }
        queue.cancelAll(TAG);
        writePrefs();
    }

    private void setAccelText(AccelerometerData acc) {
        if (acc != null) {
            dataViews[0].setText(String.format(Locale.getDefault(),"%.2f", acc.x));
            dataViews[1].setText(String.format(Locale.getDefault(),"%.2f", acc.y));
            dataViews[2].setText(String.format(Locale.getDefault(),"%.2f", acc.z));
        }
    }

    private void setLocationText(LocationData loc) {
        if (loc != null) {
            locationViews[0].setText(String.format(Locale.getDefault(),"%f", loc.latitude));
            locationViews[1].setText(String.format(Locale.getDefault(),"%f", loc.longitude));
        }
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
                mViewModel.delete(data);
            }
        }, error -> {
            // onErrorResponse: Called upon receiving an error response
            Log.e(TAG, error.toString());
            if (error instanceof TimeoutError) {
                // Sever could not be reached
                queue.cancelAll(TAG);
                uploadButton.setEnabled(true);
                Toast.makeText(MainActivity.this, "Connection timed out", Toast.LENGTH_SHORT).show();
            } else if (error instanceof ServerError && error.networkResponse.statusCode == 500) {
                // Discard local copy if server has duplicate data
                mViewModel.delete(data);
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
            uploadButton.setEnabled(true);

            // Display number of rows uploaded
            String toast_text = String.format(Locale.getDefault(), "Uploaded rows: %d/%d", numUploaded, numToUpload);
            Toast.makeText(MainActivity.this, toast_text, Toast.LENGTH_SHORT).show();
        }
    }

    private void uploadData() {
        // Query data from the local database and upload it to the server

        // Query data
        List<LocationData> locations = mViewModel.getAllLoc();
        List<AccelerometerData> accel_readings = mViewModel.getAllAccel();

        // Initialize counters
        numToUpload = locations.size() + accel_readings.size();
        numResponses = 0;
        numUploaded = 0;

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
    }

    private void writePrefs() {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("user_id", userID);
        //editor.putInt("trip_id", tripID);
        editor.apply();
    }
}