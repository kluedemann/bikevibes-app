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

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.List;
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
    private boolean isUploadEnabled = true;
    private int numUploaded;
    private int numResponses;
    private int numToUpload;
    private Button uploadButton;

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
        uploadButton = findViewById(R.id.upload_button);
        uploadButton.setOnClickListener(view -> {
            // Button is disabled
            if (!isUploadEnabled) {
                Log.d(TAG, "DISABLED!");
                return;
            }
            // Clear data from local storage
            executorService.execute(() -> {
                disableButton();
                RequestQueue queue = Volley.newRequestQueue(this);

                List<LocationData> locations = myDao.getLocation();
                List<AccelerometerData> accel_readings = myDao.getAccel();

                numToUpload = locations.size() + accel_readings.size();
                numResponses = 0;
                numUploaded = 0;

                for (int i = 0; i < locations.size(); i++) {
                    LocationData loc = locations.get(i);
                    Log.d(TAG, String.format("LOCATION: %f, %f, %d", loc.latitude, loc.longitude, loc.timestamp));
                    uploadLocation(loc, queue);
                }
                //int countLoc = myDao.clearLocation();

                for (int i = 0; i < accel_readings.size(); i++) {
                    AccelerometerData acc = accel_readings.get(i);
                    Log.d(TAG, String.format("ACCEL: %f, %f, %f, %d", acc.x, acc.y, acc.z, acc.timestamp));
                    uploadAccel(acc, queue);
                }
                //int countAccel = myDao.clearAccel();
                //Log.d(TAG, String.format("DELETED ROWS: %d, %d", countLoc, countAccel));


                if (numToUpload == 0) {
                    enableButton();
                    runOnUiThread(() -> {
                        // Display success message
                        Toast myToast = Toast.makeText(MainActivity.this, "No data", Toast.LENGTH_SHORT);
                        myToast.show();
                    });
                }
            });


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
        //Log.d(TAG, String.format("ACCELEROMETER: %f, %f, %f, %d", values[0], values[1], values[2], date.getTime()));
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
        //Log.d(TAG, String.format("LOCATION: %f, %f, %d", latitude, longitude, date.getTime()));
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

    private void uploadLocation(LocationData loc, RequestQueue queue) {
        String location_temp = "http://162.246.157.171:8080/upload/location?user_id=%s&time_stamp=%d&trip_id=%d&latitude=%f&longitude=%f";
        String location_url = String.format(Locale.US, location_temp, "test", loc.timestamp, 0, loc.latitude, loc.longitude);
        JsonObjectRequest jORequest = new JsonObjectRequest(Request.Method.POST, location_url, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                boolean isSuccess = response.optBoolean("success", false);
                Log.d(TAG, String.format("SUCCESS: %s", isSuccess));
                responseReceived(isSuccess);
                if (isSuccess) {
                    //Log.d(TAG,"ROW DELETED");
                    executorService.execute(() -> myDao.deleteLocation(loc));
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                // TODO: Handle error
                Log.e(TAG, error.toString());
                responseReceived(false);
            }
        });
        queue.add(jORequest);
    }

    private void uploadAccel(AccelerometerData acc, RequestQueue queue) {
        String accel_temp = "http://162.246.157.171:8080/upload/accelerometer?user_id=%s&time_stamp=%d&trip_id=%d&x_accel=%f&y_accel=%f&z_accel=%f";
        String accel_url = String.format(Locale.US, accel_temp, "test", acc.timestamp, 0, acc.x, acc.y, acc.z);
        JsonObjectRequest jORequest = new JsonObjectRequest(Request.Method.POST, accel_url, null, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                boolean isSuccess = response.optBoolean("success", false);
                Log.d(TAG, String.format("SUCCESS: %s", isSuccess));
                responseReceived(isSuccess);
                if (isSuccess) {
                    //Log.d(TAG,"ROW DELETED");
                    executorService.execute(() -> myDao.deleteAccel(acc));
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                // TODO: Handle error
                Log.e(TAG, error.toString());
                responseReceived(false);
            }
        });
        queue.add(jORequest);
    }

    private void responseReceived(boolean success) {
        if (success) {
            numUploaded++;
        }
        numResponses++;
        if (numResponses == numToUpload) {
            Log.d(TAG, String.format("SUCCESSES: %d, TOTAL: %d", numUploaded, numResponses));
            enableButton();
            String toast_text = String.format(Locale.getDefault(), "Uploaded rows: %d/%d", numUploaded, numToUpload);
            Toast myToast = Toast.makeText(MainActivity.this, toast_text, Toast.LENGTH_SHORT);
            myToast.show();
        }
    }

    private void disableButton() {
        isUploadEnabled = false;
        uploadButton.setBackgroundColor(getResources().getColor(R.color.grey));
    }

    private void enableButton() {
        isUploadEnabled = true;
        uploadButton.setBackgroundColor(getResources().getColor(R.color.purple_700));
    }
}