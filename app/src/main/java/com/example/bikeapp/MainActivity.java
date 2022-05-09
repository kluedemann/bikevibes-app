package com.example.bikeapp;

import androidx.appcompat.app.AppCompatActivity;
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
import android.widget.TextView;

import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {
    private static final String TAG = "MainActivity";
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private TextView[] dataViews;
    private TextView[] locationViews;
    private MyDao myDao;
    private ExecutorService executorService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get Accelerometer Sensor
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // Collect Text boxes to display data
        dataViews = new TextView[3];
        dataViews[0] = findViewById(R.id.x_accel_text);
        dataViews[1] = findViewById(R.id.y_accel_text);
        dataViews[2] = findViewById(R.id.z_accel_text);
        locationViews = new TextView[2];
        locationViews[0] = findViewById(R.id.latitude_data);
        locationViews[1] = findViewById(R.id.longitude_data);

        // Check if permissions have been granted, request them if not
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: ASSUMES PERMISSIONS ARE ALWAYS ACCEPTED
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        // Enable location tracking
        final int MIN_DELAY = 5 * 1000;
        final int MIN_DIST = 10;
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_DELAY, MIN_DIST, this);

        // Create the database and Dao
        AppDatabase db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "bike.db").build();
        myDao = db.myDao();

        // Create background thread to handle DB operations
        executorService = Executors.newFixedThreadPool(1);
    }

    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Sensor Accuracy Changed
    }

    public void onSensorChanged(SensorEvent event) {
        updateAccel(event.values);
    }

    public void onLocationChanged(Location loc) {
        updateLocation(loc.getLatitude(), loc.getLongitude());
    }

    private void updateAccel(float[] values) {
        // Update accelerometer text boxes
        Date date = new Date();
        //Log.d(TAG, String.format("%f, %f, %f, %d", values[0], values[1], values[2], date.getTime()));
        for (int i = 0; i < 3; i++) {
            dataViews[i].setText(String.format(Locale.getDefault(), "%.2f", values[i]));
        }
        //myDao.insertAccel(new AccelerometerData(date.getTime(), values[0], values[1], values[2]));
    }

    private void updateLocation(double latitude, double longitude) {
        // Update location text boxes
        Date date = new Date();
        Log.d(TAG, String.format("%f, %f, %d", latitude, longitude, date.getTime()));
        locationViews[0].setText(String.format(Locale.getDefault(),"%f", latitude));
        locationViews[1].setText(String.format(Locale.getDefault(), "%f", longitude));
        executorService.execute(() -> myDao.insertLocation(new LocationData(date.getTime(), latitude, longitude)));
    }
}