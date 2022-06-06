package com.example.bikeapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.room.Room;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {
    private static final String TAG = "MainActivity";
    private SensorManager mSensorManager;
    private LocationManager locationManager;
    private Sensor mAccelerometer;
    private TextView[] dataViews;
    private TextView[] locationViews;
    private TrackingViewModel mViewModel;
    private ExecutorService executorService;
//    private TrackingDao myDao;
//    private boolean isTracking = false;
    private int numUploaded;
    private int numResponses;
    private int numToUpload;
    private Button uploadButton;
    private RequestQueue queue;
    private String userID;
    private int tripID;

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
        tripID = sharedPref.getInt("trip_id", 0);

        // Initialize user/trip ids on first opening of app
        if (userID == null) {
            userID = UUID.randomUUID().toString();
            //userID = "test";
            writePrefs();
        }

        // Get Accelerometer Sensor
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        if (mAccelerometer == null) {
            mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        /*  List Sensors
        List<Sensor> sensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);
        for (int i = 0; i < sensors.size(); i++) {
            Log.d(TAG, sensors.get(i).toString());
        }
        */

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
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // Create the database and Dao
        //AppDatabase db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "bike.db").build();
        //myDao = db.myDao();

        // Create background thread to handle DB operations
        executorService = Executors.newFixedThreadPool(1);

        // Handle Tracking switch
        SwitchCompat mySwitch = findViewById(R.id.tracking_switch);
        mySwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            mViewModel.setTracking(b);
            if (mViewModel.getTracking()) {
                tripID++;
                Log.d(TAG, "TRACKING STARTED");
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                // TODO: Move this to other side and test it
                writePrefs();
                Log.d(TAG, "TRACKING ENDED");
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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

        // Begin tracking accelerometer again
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        // TODO: Still crashes if you disable location while app is open (use BroadcastReceiver?)
        // Begin location tracking if it is enabled
        final int MIN_DELAY = 5 * 1000;
        final int MIN_DIST = 10;
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_DELAY, MIN_DIST, this);
        }

        uploadButton.setEnabled(true);
    }

    protected void onPause() {
        // Stop all background processes upon app being paused
        super.onPause();
        mSensorManager.unregisterListener(this);
        locationManager.removeUpdates(this);
        queue.cancelAll(TAG);
        writePrefs();
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Sensor Accuracy Changed
    }

    public void onSensorChanged(SensorEvent event) {
        // TODO: Differentiate between accelerometer and linear acceleration
        Date date = new Date();
        AccelerometerData acc = new AccelerometerData(date.getTime(), event.values[0], event.values[1], event.values[2], tripID);
        mViewModel.setAccel(acc);
        //updateAccel(event.values);
    }

    public void onLocationChanged(Location loc) {
        Date date = new Date();
        LocationData locData = new LocationData(date.getTime(), loc.getLatitude(), loc.getLongitude(), tripID);
        mViewModel.setLoc(locData);
        //updateLocation(loc.getLatitude(), loc.getLongitude());
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

//    private void updateAccel(float[] values) {
//        // Track accelerometer values and upload to local database
//        // values - the x, y, and z components of acceleration in m/s^2
//
//        // Update accelerometer text boxes
//        Date date = new Date();
//        for (int i = 0; i < 3; i++) {
//            dataViews[i].setText(String.format(Locale.getDefault(), "%.2f", values[i]));
//        }
//        //Log.d(TAG, String.format("ACCELEROMETER: %f, %f, %f, %d", values[0], values[1], values[2], date.getTime()));
//
//        // Store Data in local database
//        if (isTracking) {
//            AccelerometerData accelData = new AccelerometerData(date.getTime(), values[0], values[1], values[2], tripID);
//            executorService.execute(() -> myDao.insertAccel(accelData));
//        }
//    }

//    private void updateLocation(double latitude, double longitude) {
//        // Track GPS coordinates and upload to local database
//        // latitude, longitude - the coordinates received from the GPS
//
//        // Update location text boxes
//        Date date = new Date();
//        locationViews[0].setText(String.format(Locale.getDefault(),"%f", latitude));
//        locationViews[1].setText(String.format(Locale.getDefault(), "%f", longitude));
//        //Log.d(TAG, String.format("LOCATION: %f, %f, %d", latitude, longitude, date.getTime()));
//
//        // Store Data in Local Database
//        if (isTracking) {
//            LocationData locData = new LocationData(date.getTime(), latitude, longitude, tripID);
//            executorService.execute(() -> myDao.insertLocation(locData));
//        }
//    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // Called upon LocationListener status changing
        // Removes error on old API
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
        editor.putInt("trip_id", tripID);
        editor.apply();
    }
}