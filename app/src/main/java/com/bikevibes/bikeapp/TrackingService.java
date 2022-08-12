package com.bikevibes.bikeapp;

import static android.opengl.Matrix.multiplyMV;
import static android.opengl.Matrix.transposeM;

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
import androidx.preference.PreferenceManager;

import com.bikevibes.bikeapp.db.AccelerometerData;
import com.bikevibes.bikeapp.db.LocationData;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TrackingService extends Service {
    private static final String TAG = "TrackingService";

    private int tripID;
    private boolean isTracking = false;
    private SensorManager sensorManager;
    private final IBinder binder = new LocalBinder();
    private DataRepository repository;
    private PowerManager.WakeLock wakeLock;

    private AccelTracker accelTracker;
    private LocationTracker locationTracker;
    private RotationTracker rotationTracker;

    // Binder class to return the Service
    public class LocalBinder extends Binder {
        TrackingService getService() {
            return TrackingService.this;
        }
    }

    class AccelTracker implements SensorEventListener {
        private static final int SENSOR_DELAY = 200000;
        private static final int CACHE_SIZE = 25;
        private static final float TIME_CONSTANT = 1.8f;

        private List<AccelerometerData> accelCache = new ArrayList<>();
        private final float[] gravity = new float[3];
        private Date previous = null;
        private final Sensor accelerometer;

        public AccelTracker() {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        public void start() {
            sensorManager.registerListener(this, accelerometer, SENSOR_DELAY);
        }

        public void stop() {
            sensorManager.unregisterListener(this);
            flush();
        }

        private void flush() {
            repository.insertAccelBatch(accelCache);
            accelCache = new ArrayList<>();
        }

        @NonNull
        private AccelerometerData getData(SensorEvent event) {
            Date date = new Date();
            float[] linear_acceleration = new float[4];
            if (rotationTracker.isActive()) {
                float[] temp = new float[4];
                temp[0] = event.values[0];
                temp[1] = event.values[1];
                temp[2] = event.values[2];
                temp[3] = 0;
                float[] rotation = new float[16];
                transposeM(rotation, 0, rotationTracker.getRotationMatrix(), 0);
                multiplyMV(linear_acceleration, 0, rotation, 0, temp, 0);
                linear_acceleration[2] -= 9.81f;
            } else {

                float dt = 0.2f;
                if (previous != null) {
                    dt = (date.getTime() - previous.getTime()) / 1000.0f;
                }
                previous = date;
                final float alpha = TIME_CONSTANT / (TIME_CONSTANT + dt);

                // Isolate the force of gravity with the low-pass filter.
                gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
                gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
                gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

                // Remove the gravity contribution with the high-pass filter.
                linear_acceleration[0] = event.values[0] - gravity[0];
                linear_acceleration[1] = event.values[1] - gravity[1];
                linear_acceleration[2] = event.values[2] - gravity[2];
            }
            return new AccelerometerData(date, linear_acceleration, tripID);
        }

        @Override
        public void onSensorChanged(@NonNull SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                accelCache.add(getData(event));
                if (accelCache.size() == CACHE_SIZE) {
                    flush();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }
    }

    class LocationTracker implements LocationListener {
        private static final int CACHE_SIZE = 1;
        private static final int MIN_DELAY = 5 * 1000;
        private static final int MIN_DIST = 10;

        private List<LocationData> locCache = new ArrayList<>();
        private final LocationManager locationManager;

        public LocationTracker() {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        }

        public void start() {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && ContextCompat.checkSelfPermission(TrackingService.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_DELAY, MIN_DIST, this);
            }
        }

        public void stop() {
            locationManager.removeUpdates(this);
            flush();
        }

        private void flush() {
            repository.insertLocBatch(locCache);
            locCache = new ArrayList<>();
        }

        @Override
        public void onLocationChanged(@NonNull Location loc) {
            Date date = new Date();
            LocationData locData = new LocationData(date, loc.getLatitude(), loc.getLongitude(), tripID);
            locCache.add(locData);
            if (locCache.size() == CACHE_SIZE) {
                flush();
            }
        }
    }

    class RotationTracker implements SensorEventListener {
        private static final int SENSOR_DELAY = 1000000;

        private final boolean isActive;
        private final float[] rotationMatrix;
        private final Sensor rotationSensor;

        public RotationTracker() {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            isActive = rotationSensor != null;

            rotationMatrix = new float[16];
            rotationMatrix[0] = 1;
            rotationMatrix[5] = 1;
            rotationMatrix[10] = 1;
            rotationMatrix[15] = 1;
        }

        public void start() {
            if (isActive) {
                sensorManager.registerListener(this, rotationSensor, SENSOR_DELAY);
            }
        }

        public void stop() {
            sensorManager.unregisterListener(this);
        }

        public boolean isActive() {
            return isActive;
        }

        public float[] getRotationMatrix() {
            return rotationMatrix;
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

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
        rotationTracker = new RotationTracker();
        accelTracker = new AccelTracker();
        locationTracker = new LocationTracker();

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
                .setSmallIcon(R.drawable.small_icon) // notification icon
                .setContentTitle(getString(R.string.app_name)) // title for notification
                .setContentText(getString(R.string.tracking_notification_text))// message for notification
                .setAutoCancel(true).build(); // clear notification after click
        startForeground(NOTIFICATION_ID, notification);

        tripID++;
        isTracking = true;
        startListening();
        //writePrefs();
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
        rotationTracker.stop();
        accelTracker.stop();
        locationTracker.stop();
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    /**
     * Register listeners for accelerometer and location updates.
     */
    private void startListening() {
        rotationTracker.start();
        accelTracker.start();
        locationTracker.start();
    }

    /**
     * Stop storing data, release WakeLock, and remove the service from the foreground.
     */
    void disableTracking() {
        Log.d(TAG, "Tracking stopped!");
        isTracking = false;

        rotationTracker.stop();
        accelTracker.stop();
        locationTracker.stop();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int blackoutRadius = prefs.getInt("privacy_radius", 50);
        repository.createSegments(tripID, blackoutRadius);
        writePrefs();

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

    int getTripID() {
        return tripID;
    }
}
