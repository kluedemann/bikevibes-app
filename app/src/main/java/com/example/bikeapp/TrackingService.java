package com.example.bikeapp;

import android.Manifest;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.bikeapp.db.AccelerometerData;
import com.example.bikeapp.db.LocationData;

import java.util.Date;

public class TrackingService extends Service implements SensorEventListener, LocationListener {
    private static final String TAG = "TrackingService";

    private int tripID;
    private boolean isTracking = false;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private LocationManager locationManager;
    private final IBinder binder = new LocalBinder();
    private DataRepository mRepository;
    private boolean isLinear = true;
    private final float[] gravity = new float[3];

    // Binder class to return the Service
    public class LocalBinder extends Binder {
        TrackingService getService() {
            return TrackingService.this;
        }
    }

    /**
     * Initialize the service when it is first created.
     * Get the repository, accelerometer sensor, LocationManager, and current tripID.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        BikeApp app = (BikeApp) getApplication();
        mRepository = app.getRepository();

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        if (mAccelerometer == null) {
            isLinear = false;
            mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        String PREFS = getString(R.string.preference_file_key);
        SharedPreferences sharedPref = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        tripID = sharedPref.getInt("trip_id", 0);
    }

    /**
     * Begin tracking data when bound to by an activity.
     * @param intent - the message to bind to the service
     * @return binder - used to get the service object
     */
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Bound!");
        startTracking();
        return binder;
    }

    /**
     * Begin storing data and start activity in the foreground.
     * Called from startService when the tracking switch is toggled.
     * @param intent - the message to start the service
     * @param flags - additional data about the start request
     * @param startID - can be used to distinguish between service tasks
     * @return START_NOT_STICKY - indicates that the system should not recreate the service
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startID) {
        Log.d(TAG, "Started!");

        Notification notification = new NotificationCompat.Builder(getApplicationContext(), "1")
                .setSmallIcon(R.mipmap.ic_launcher) // notification icon
                .setContentTitle("BikeApp") // title for notification
                .setContentText("Tracking data")// message for notification
                .setAutoCancel(true).build(); // clear notification after click
        startForeground(1, notification);

        tripID++;
        isTracking = true;
        startTracking();

        return START_NOT_STICKY;
    }

    /**
     * Start tracking when activity binds to service again.
     * Called when activity has already unbound from the service.
     * @param intent - the message to rebind to the service
     */
    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "Rebound!");
        startTracking();
    }

    /**
     * Unbind the activity from the service.
     * If not storing data, unregister listeners and stop service.
     * @param intent - the message to unbind from the service
     * @return - true - flag that indicates that onRebind will be called
     * if the activity binds to the service again
     */
    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Unbound!");
        if (!isTracking) {
            mSensorManager.unregisterListener(this);
            locationManager.removeUpdates(this);
            writePrefs();
            Log.d(TAG, "Stopped!");
            stopSelf();
        }
        return true;
    }

    /**
     * Unregister listeners and remove updates.
     * Called when the service is destroyed by the system.
     */
    @Override
    public void onDestroy() {
        Log.d(TAG, "Destroyed!");
        mSensorManager.unregisterListener(this);
        locationManager.removeUpdates(this);
        writePrefs();
    }

    /**
     * Update the accelerometer records with the new sensor reading.
     * If storing data, insert the record into the database.
     * If using raw accelerometer data, filter out gravity.
     * Otherwise, assume that linear acceleration is used, which filters gravity automatically.
     * Called when a SensorEvent is received from the system.
     * @param event - the SensorEvent triggered by the accelerometer
     */
    @Override
    public void onSensorChanged(SensorEvent event) {

        float[] linear_acceleration = event.values;
        Date date = new Date();

        if (!isLinear) {
            final float alpha = (float) 0.4;

            // Isolate the force of gravity with the low-pass filter.
            gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
            gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
            gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

            // Remove the gravity contribution with the high-pass filter.
            linear_acceleration[0] = event.values[0] - gravity[0];
            linear_acceleration[1] = event.values[1] - gravity[1];
            linear_acceleration[2] = event.values[2] - gravity[2];
        }
        AccelerometerData acc = new AccelerometerData(date.getTime(), linear_acceleration, tripID);
        //Log.d("Service", "Accel");
        mRepository.setAccel(acc);
        if (isTracking) {
            mRepository.insert(acc);
        }
    }

    /**
     * Called when a Sensor reports an accuracy change.
     * Currently not implemented.
     * @param sensor - the sensor whose accuracy changed
     * @param i - the new accuracy of the sensor (SensorManager.SENSOR_STATUS_* flag)
     */
    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        // no-op
    }

    /**
     * Update the current location when an update is received from the GPS.
     * If storing data, insert it into the database.
     * Called when the LocationManager receives an update from the GPS
     * @param loc- the new location; contains latitude and longitude
     */
    @Override
    public void onLocationChanged(@NonNull Location loc) {
        Date date = new Date();
        LocationData locData = new LocationData(date.getTime(), loc.getLatitude(), loc.getLongitude(), tripID);
        mRepository.setLoc(locData);
        //Log.d("Service", "Location");
        if (isTracking) {
            mRepository.insert(locData);
        }
    }

    /**
     * Register listeners for accelerometer and location updates.
     */
    private void startTracking() {
        final int MIN_DELAY = 5 * 1000;
        final int MIN_DIST = 10;

        if (mAccelerometer != null) {
            mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_DELAY, MIN_DIST, this);
        }
    }

    /**
     * Stop storing data and remove the service from the foreground.
     */
    void disableTracking() {
        Log.d(TAG, "Tracking stopped!");
        isTracking = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        }
        writePrefs();
    }

    /**
     * Update the tripID stored in the SharedPreferences file.
     */
    private void writePrefs() {
        String PREFS = "com.example.bikeapp.TRACKING_INFO";
        SharedPreferences sharedPref = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt("trip_id", tripID);
        editor.apply();
    }

    boolean getTracking() {
        return isTracking;
    }
}
