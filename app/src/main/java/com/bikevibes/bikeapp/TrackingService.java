package com.bikevibes.bikeapp;

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
import android.opengl.Matrix;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.bikevibes.bikeapp.db.AccelerometerData;
import com.bikevibes.bikeapp.db.LocationData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Tracks the accelerometer, location, and rotation data of the device.
 * Data is only collected when the tracking switch is activated.
 * Binds to the MainActivity when it is opened.
 * Runs as a foreground service as long as the switch is active.
 */
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

        // Initialize tracker objects
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotationTracker = new RotationTracker();
        accelTracker = new AccelTracker();
        locationTracker = new LocationTracker();

        // Get tripID
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

        // Create notification and start in foreground
        final int NOTIFICATION_ID = 1;
        Notification notification = new NotificationCompat.Builder(getApplicationContext(), getString(R.string.tracking_channel_id))
                .setSmallIcon(R.drawable.small_icon) // notification icon
                .setContentTitle(getString(R.string.app_name)) // title for notification
                .setContentText(getString(R.string.tracking_notification_text))// message for notification
                .setAutoCancel(true).build(); // clear notification after click
        startForeground(NOTIFICATION_ID, notification);

        // Start tracking
        final long WAKELOCK_TIMEOUT = 10 * 60 * 60 * 1000L; // 10 hours
        tripID++;
        isTracking = true;
        startListening();
        wakeLock.acquire(WAKELOCK_TIMEOUT);

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
        stopListening();
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
     * Unregister listeners for accelerometer and location updates.
     */
    private void stopListening() {
        rotationTracker.stop();
        accelTracker.stop();
        locationTracker.stop();
    }

    /**
     * Stop storing data, release WakeLock, and remove the service from the foreground.
     */
    void disableTracking() {
        Log.d(TAG, "Tracking stopped!");
        isTracking = false;

        stopListening();

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

    // ********************* Getter Methods ******************************************************
    boolean getTracking() {
        return isTracking;
    }

    int getTripID() {
        return tripID;
    }

    // ********************* Inner Classes *********************************************************
    /**
     * Binder class passed to the MainActivity.
     * Provides access to the TrackingService instance.
     */
    public class LocalBinder extends Binder {
        TrackingService getService() {
            return TrackingService.this;
        }
    }

    /**
     * Track the device accelerometer and upload data to the repository.
     */
    class AccelTracker implements SensorEventListener {
        private static final int SENSOR_DELAY = 200000;
        private static final int CACHE_SIZE = 250;
        private static final float TIME_CONSTANT = 1.8f;
        private static final int MAX_LATENCY = 1000000;

        private List<AccelerometerData> accelCache = new ArrayList<>();
        private final float[] gravity = new float[3];
        private final long diff;
        private Date previous = null;
        private final Sensor accelerometer;

        /**
         * Initialize the AccelTracker object.
         */
        public AccelTracker() {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            diff = new Date().getTime() - SystemClock.elapsedRealtime();
            gravity[2] = 9.81f;
        }

        /**
         * Start listening for sensor values.
         */
        public void start() {
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SENSOR_DELAY, MAX_LATENCY);
            }
        }

        /**
         * Stop listening for sensor updates and flush the cache.
         */
        public void stop() {
            sensorManager.unregisterListener(this);
            flush();
        }

        /**
         * Insert all records in the cache into the database and clear the cache.
         */
        private void flush() {
            repository.insertAccelBatch(accelCache);
            accelCache = new ArrayList<>();
        }

        /**
         * Produce an accelerometer record from a sensor reading.
         * @param event - the accelerometer reading
         * @return - the AccelerometerData record
         */
        @NonNull
        private AccelerometerData getAccel(SensorEvent event) {
            Date timestamp = getTimestamp(event);

            // Update rotation from accelerometer reading if no gyroscope
            if (!rotationTracker.isActive()) {
                updateRotation(event.values, timestamp);
            }

            // Get accelerometer values rotated relative to Earth
            // See https://developer.android.com/guide/topics/sensors/sensors_motion#sensors-motion-rotate
            float[] accel = getRotatedAccel(event.values);
            return new AccelerometerData(timestamp, accel, tripID);
        }

        /**
         * Return the Unix timestamp at which the event occurred, in ms.
         * @param event - the accelerometer reading
         * @return - the Unix timestamp in ms as a Date object
         */
        @NonNull
        private Date getTimestamp(@NonNull SensorEvent event) {
            return new Date(event.timestamp / 1000000 + diff);
        }

        /**
         * Update the rotation applied to the accelerometer reading based
         * on the gravity vector isolated using a low-pass filter.
         * @param rawAccel - the raw sensor values relative to the device
         * @param timestamp - the time at which the event occurred
         */
        private void updateRotation(float[] rawAccel, Date timestamp) {
            // Calculate the alpha value used in the low-pass filter from the time delta
            float dt = 0.2f;
            if (previous != null) {
                dt = (timestamp.getTime() - previous.getTime()) / 1000.0f;
            }
            previous = timestamp;
            final float alpha = TIME_CONSTANT / (TIME_CONSTANT + dt);

            // Isolate the force of gravity with the low-pass filter.
            // Uses an exponentially weighted average.
            // See https://developer.android.com/guide/topics/sensors/sensors_motion#sensors-motion-accel
            // Or https://en.wikipedia.org/wiki/Low-pass_filter#Simple_infinite_impulse_response_filter
            // NOTE: For Wikipedia, replace alpha with 1 - alpha
            gravity[0] = alpha * gravity[0] + (1 - alpha) * rawAccel[0];
            gravity[1] = alpha * gravity[1] + (1 - alpha) * rawAccel[1];
            gravity[2] = alpha * gravity[2] + (1 - alpha) * rawAccel[2];

            // Update the rotation matrix
            rotationTracker.setRotationMatrix(gravity);
        }

        /**
         * Determine the acceleration relative to the Earth-based coordinate system.
         * See https://developer.android.com/guide/topics/sensors/sensors_motion#sensors-motion-rotate
         * @param rawAccel - the raw accelerometer readings, using device-based coordinates
         * @return - the rotated acceleration vector
         */
        @NonNull
        private float[] getRotatedAccel(@NonNull float[] rawAccel) {
            // Copy raw values into 4D vector
            float[] temp = new float[4];
            temp[0] = rawAccel[0];
            temp[1] = rawAccel[1];
            temp[2] = rawAccel[2];
            temp[3] = 0;

            // Apply rotation and subtract gravity
            float[] accel = new float[4];
            Matrix.multiplyMV(accel, 0, rotationTracker.getRotationMatrix(), 0, temp, 0);
            accel[2] -= 9.81f;
            return accel;
        }

        /**
         * Create an accelerometer record and flush the cache once it reaches a given size.
         * Called when the accelerometer delivers its readings.
         * @param event - the accelerometer reading
         */
        @Override
        public void onSensorChanged(@NonNull SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                accelCache.add(getAccel(event));
                if (accelCache.size() == CACHE_SIZE) {
                    flush();
                }
            }
        }

        /**
         * NOT USED
         * Called when the accelerometer's accuracy changes.
         * @param sensor - the accelerometer sensor
         * @param i - the new accuracy level (int constant)
         */
        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }
    }

    /**
     * Track the device's location using the GPS and insert records into the database.
     */
    class LocationTracker implements LocationListener {
        private static final int CACHE_SIZE = 10;
        private static final int MIN_DELAY = 5 * 1000;
        private static final int MIN_DIST = 10;

        private List<LocationData> locCache = new ArrayList<>();
        private final LocationManager locationManager;

        /**
         * Initialize the LocationTracker object
         */
        public LocationTracker() {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        }

        /**
         * Request location updates if location services is active and the permission is enabled.
         * Called when the tracking switch is activated.
         */
        public void start() {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && ContextCompat.checkSelfPermission(TrackingService.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_DELAY, MIN_DIST, this);
            }
        }

        /**
         * Stop tracking location data and flush the cache.
         */
        public void stop() {
            locationManager.removeUpdates(this);
            flush();
        }

        /**
         * Insert cached records into the database and clear the cache.
         */
        private void flush() {
            repository.insertLocBatch(locCache);
            locCache = new ArrayList<>();
        }

        /**
         * Record the GPS location and add it to the cache.
         * Called when the GPS receives a signal that is at least MIN_DIST metres away and
         * MIN_DELAY seconds after the previous location received.
         * @param loc - the GPS location received
         */
        @Override
        public void onLocationChanged(@NonNull Location loc) {
            Date timestamp = new Date();
            LocationData locData = new LocationData(timestamp, loc.getLatitude(), loc.getLongitude(), tripID);
            locCache.add(locData);
            if (locCache.size() == CACHE_SIZE) {
                flush();
            }
        }
    }

    /**
     * Track the device's rotation using the gyroscope if available.
     * Holds the matrix applied to the raw accelerometer readings to rotate them.
     */
    class RotationTracker implements SensorEventListener {
        private static final int SENSOR_DELAY = 200000;
        private static final int MAX_LATENCY = 1000000;

        private final boolean isActive;
        private final float[] rotationMatrix = new float[16];
        private final Sensor rotationSensor;

        /**
         * Initialize the RotationTracker
         */
        public RotationTracker() {
            rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            isActive = rotationSensor != null;

            // Default: identity matrix (no rotation)
            addIdentity();
        }

        /**
         * Listen for gyroscope updates if the device has one.
         * Called when the tracking switch is activated.
         */
        public void start() {
            if (isActive) {
                sensorManager.registerListener(this, rotationSensor, SENSOR_DELAY, MAX_LATENCY);
            }
        }

        /**
         * Stop listening for gyroscope updates.
         */
        public void stop() {
            sensorManager.unregisterListener(this);
        }

        /**
         * Update the rotation matrix using the gravity isolated from the raw
         * accelerometer readings using a low-pass filter.
         * See https://math.stackexchange.com/questions/180418/calculate-rotation-matrix-to-align-vector-a-to-vector-b-in-3d
         * @param gravity - the gravity vector isolated from low-pass filter
         */
        public void setRotationMatrix(float[] gravity) {
            // Rotate the normalized gravity vector g = (g1, g2, g3) = gravity / || gravity ||
            // onto the normal z vector z = (0, 0, 1)
            // Uses the formula R = I + vx + (1 / (1 + c)) vx^2
            // v = (v1, v2, 0) = g x z = (g2, -g1, 0)
            // c = cos theta = g * z = g3

            // Ignore if zero vector
            float MAGNITUDE = norm(gravity);
            if (MAGNITUDE == 0) {
                return;
            }

            float v1 = gravity[1] / MAGNITUDE;
            float v2 = - gravity[0] / MAGNITUDE;
            float c = gravity[2] / MAGNITUDE;

            // Set to reflection
            if (c == -1) {
                setReflection();
                return;
            }

            // Get vx and vx^2
            float[] vx = getVX(v1, v2);
            float[] vx2 = new float[16];
            Matrix.multiplyMM(vx2, 0, vx, 0, vx, 0);

            // Scale and add
            scale(vx2, 1f / (1 + c));
            add(rotationMatrix, vx2, vx);
            addIdentity();
        }

        /**
         * Add the identity matrix to the current rotation matrix
         */
        private void addIdentity() {
            rotationMatrix[0] += 1;
            rotationMatrix[5] += 1;
            rotationMatrix[10] += 1;
            rotationMatrix[15] += 1;
        }

        /**
         * Set the rotation matrix to flip the accelerometer reading
         */
        private void setReflection() {
            Arrays.fill(rotationMatrix, 0);
            rotationMatrix[0] = -1;
            rotationMatrix[5] = -1;
            rotationMatrix[10] = -1;
            rotationMatrix[15] = 1;
        }

        /**
         * Add vectors/matrices v1 and v2, and store the result in result.
         * @param result - the array to store the result in
         * @param v1 - the first vector
         * @param v2 - the second vector
         * @throws IllegalArgumentException - arrays of different lengths
         */
        private void add(@NonNull float[] result, @NonNull float[] v1, @NonNull float[] v2) {
            if (v2.length != v1.length || result.length != v1.length) {
                throw new IllegalArgumentException("Arrays must be of the same length.");
            }
            for (int i = 0; i < v1.length; i++) {
                result[i] = v1[i] + v2[i];
            }
        }

        /**
         * Return the vx matrix from values v1 and v2.
         * See https://math.stackexchange.com/questions/180418/calculate-rotation-matrix-to-align-vector-a-to-vector-b-in-3d
         * NOTE: OpenGL implements multiplication using column-major order
         * @param v1 - the first coordinate of g x z
         * @param v2 - the second coordinate of g x z
         * @return - the 4x4 vx matrix
         */
        @NonNull
        private float[] getVX(float v1, float v2) {
            float[] vx = new float[16];
            vx[2] = -v2;
            vx[6] = v1;
            vx[8] = v2;
            vx[9] = -v1;
            return vx;
        }

        /**
         * Scale a vector in place by factor c.
         * @param vector - the vector to be scaled
         * @param c - the scaling constant
         */
        private void scale(@NonNull float[] vector, float c) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] = c * vector[i];
            }
        }

        /**
         * Return the length of the given vector
         * @param vector - the vector to measure
         * @return - the Euclidean norm of the vector
         */
        private float norm(@NonNull float[] vector) {
            float sum = 0;
            for (float v : vector) {
                sum += v * v;
            }
            return (float) Math.sqrt(sum);
        }

        /**
         * Update the rotation matrix given the rotation of the device.
         * Rotation matrix is the transformation to map device-relative acceleration onto the
         * Earth-based coordinate system.
         * NOTE: The rotation matrix is in column-major order
         * @param event - the rotation vector reading; last 3 components of unit quaternion
         */
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                float[] rotation = new float[16];
                SensorManager.getRotationMatrixFromVector(rotation, event.values);
                Matrix.transposeM(rotationMatrix, 0, rotation, 0);
            }
        }

        /**
         * NOT USED
         * Called when the sensor's accuracy changes.
         * @param sensor - the rotation vector sensor
         * @param i - the new accuracy level; int code
         */
        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }

        // ************************* Getter methods *******************************************

        public boolean isActive() {
            return isActive;
        }

        public float[] getRotationMatrix() {
            return rotationMatrix;
        }
    }
}
