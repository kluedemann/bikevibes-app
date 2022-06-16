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
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.bikeapp.db.AccelerometerData;
import com.example.bikeapp.db.LocationData;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TrackingService extends Service implements SensorEventListener, LocationListener {
    private static final String TAG = "TrackingService";

    private int tripID;
    private boolean isTracking = false;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private LocationManager locationManager;
    private final IBinder binder = new LocalBinder();
    private DataRepository repository;
    private boolean isLinear = true;
    private final float[] gravity = new float[3];
    private PowerManager.WakeLock wakeLock;
    private List<AccelerometerData> accelCache = new ArrayList<>();
    private List<LocationData> locCache = new ArrayList<>();
    private int numInserted;

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
        repository = app.getRepository();

        // Create wakelock
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getString(R.string.wakelock));

        // Get accelerometer sensor
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        if (accelerometer == null) {
            isLinear = false;
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        String PREFS = getString(R.string.preference_file_key);
        SharedPreferences sharedPref = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        tripID = sharedPref.getInt(getString(R.string.prefs_trip_key), 0);
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
        super.onStartCommand(intent, flags, startID);
        Log.d(TAG, "Started!");

        final int NOTIFICATION_ID = 1;
        Notification notification = new NotificationCompat.Builder(getApplicationContext(), getString(R.string.tracking_channel_id))
                .setSmallIcon(R.mipmap.ic_launcher) // notification icon
                .setContentTitle(getString(R.string.app_name)) // title for notification
                .setContentText(getString(R.string.tracking_notification_text))// message for notification
                .setAutoCancel(true).build(); // clear notification after click
        startForeground(NOTIFICATION_ID, notification);

        numInserted = 0;
        tripID++;
        isTracking = true;
        startTracking();
        writePrefs();
        wakeLock.acquire(10*60*60*1000L); /*10 Hour Timeout*/

        return START_NOT_STICKY;
    }

    /**
     * Start tracking when activity binds to service again.
     * Called when activity has already unbound from the service.
     * @param intent - the message to rebind to the service
     */
    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
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
        super.onUnbind(intent);
        Log.d(TAG, "Unbound!");
        if (!isTracking) {
            sensorManager.unregisterListener(this);
            locationManager.removeUpdates(this);
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
        super.onDestroy();
        Log.d(TAG, "Destroyed!");
        sensorManager.unregisterListener(this);
        locationManager.removeUpdates(this);
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
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
    public void onSensorChanged(@NonNull SensorEvent event) {

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
        repository.setAccel(acc);
        if (isTracking) {
            //Log.d(TAG, String.format(Locale.getDefault(), "Size: %d", accelCache.size()));
            accelCache.add(acc);
            if (accelCache.size() == 150) {
                //Log.d(TAG, "INSERTING ACCEL");
                numInserted += 150;
                repository.insertAccelBatch(accelCache);
                accelCache = new ArrayList<>();
            }
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
        repository.setLoc(locData);
        if (isTracking) {
            //repository.insert(locData);
            locCache.add(locData);
            if (locCache.size() == 6) {
                numInserted += 6;
                repository.insertLocBatch(locCache);
                locCache = new ArrayList<>();
            }
        }
    }

    /**
     * Register listeners for accelerometer and location updates.
     */
    private void startTracking() {
        final int MIN_DELAY = 5 * 1000;
        final int MIN_DIST = 10;

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_DELAY, MIN_DIST, this);
        }
    }

    /**
     * Stop storing data, release WakeLock, and remove the service from the foreground.
     */
    void disableTracking() {
        Log.d(TAG, "Tracking stopped!");
        isTracking = false;

        repository.insertLocBatch(locCache);
        repository.insertAccelBatch(accelCache);
        numInserted += locCache.size() + accelCache.size();
        locCache = new ArrayList<>();
        accelCache = new ArrayList<>();
        Log.d(TAG, String.format("INSERTED: %d", numInserted));

        if (wakeLock.isHeld()) {
            wakeLock.release();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        }
    }

    /**
     * Update the tripID stored in the SharedPreferences file.
     */
    private void writePrefs() {
        final String PREFS = getString(R.string.preference_file_key);
        SharedPreferences sharedPref = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt(getString(R.string.prefs_trip_key), tripID);
        editor.apply();
    }

    boolean getTracking() {
        return isTracking;
    }
}
