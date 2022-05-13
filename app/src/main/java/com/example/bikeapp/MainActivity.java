package com.example.bikeapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.room.Room;

import android.Manifest;
import android.content.Context;
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
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {
    private static final String TAG = "MainActivity";
    private SensorManager mSensorManager;
    private LocationManager locationManager;
    private Sensor mAccelerometer;
    private TextView[] dataViews;
    private TextView[] locationViews;
    private MyDao myDao;
    private ExecutorService executorService;
    private boolean isTracking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // Create the database and Dao
        AppDatabase db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "bike.db").build();
        myDao = db.myDao();

        // Create background thread to handle DB operations
        executorService = Executors.newFixedThreadPool(1);

        // Handle Tracking switch
        SwitchCompat mySwitch = findViewById(R.id.tracking_switch);
        mySwitch.setOnCheckedChangeListener((compoundButton, b) -> {
            isTracking = b;
            if (isTracking) {
                Log.d(TAG, "TRACKING STARTED");
            } else {
                Log.d(TAG, "TRACKING ENDED");
            }
        });

        // Handle Upload Button
        // TODO: Currently only deletes local data to prevent using all space in testing
        Button myButton = findViewById(R.id.upload_button);
        myButton.setOnClickListener(view -> {
            // Clear data from local storage
            executorService.execute(() -> {
                int countLoc = myDao.clearLocation();
                int countAccel = myDao.clearAccel();
                Log.d(TAG, String.format("DELETED ROWS: %d, %d", countLoc, countAccel));
            });

            // Display success message
            Toast myToast = Toast.makeText(this, R.string.uploaded_data_text, Toast.LENGTH_SHORT);
            myToast.show();
        });
    }

    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        // Check if permissions have been granted, request them if not
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: ASSUMES PERMISSIONS ARE ALWAYS ACCEPTED
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        // TODO: Still crashes if you disable location while app is open (use BroadcastReceiver?)
        // Ensure that location is enabled
        final int MIN_DELAY = 5 * 1000;
        final int MIN_DIST = 10;
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_DELAY, MIN_DIST, this);
        }
    }

    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
        //locationManager.removeUpdates(this);
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Sensor Accuracy Changed
    }

    // TODO: Differentiate between accelerometer and linear acceleration
    public void onSensorChanged(SensorEvent event) {
        updateAccel(event.values);
    }

    public void onLocationChanged(Location loc) {
        updateLocation(loc.getLatitude(), loc.getLongitude());
    }

    private void updateAccel(float[] values) {
        // Update accelerometer text boxes
        Date date = new Date();
        Log.d(TAG, String.format("ACCELEROMETER: %f, %f, %f, %d", values[0], values[1], values[2], date.getTime()));
        for (int i = 0; i < 3; i++) {
            dataViews[i].setText(String.format(Locale.getDefault(), "%.2f", values[i]));
        }

        // Store Data in local database
        if (isTracking) {
            AccelerometerData accelData = new AccelerometerData(date.getTime(), values[0], values[1], values[2]);
            executorService.execute(() -> myDao.insertAccel(accelData));
        }
    }

    private void updateLocation(double latitude, double longitude) {
        // Update location text boxes
        Date date = new Date();
        Log.d(TAG, String.format("LOCATION: %f, %f, %d", latitude, longitude, date.getTime()));
        locationViews[0].setText(String.format(Locale.getDefault(),"%f", latitude));
        locationViews[1].setText(String.format(Locale.getDefault(), "%f", longitude));

        // Store Data in Local Database
        if (isTracking) {
            LocationData locData = new LocationData(date.getTime(), latitude, longitude);
            executorService.execute(() -> myDao.insertLocation(locData));
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // Removes error on old API
    }

}