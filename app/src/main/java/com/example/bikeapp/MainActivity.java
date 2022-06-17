package com.example.bikeapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.bikeapp.db.AccelerometerData;
import com.example.bikeapp.db.LocationData;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Holds the UI elements for the app. Created on app startup.
 * Binds to and starts Services and launches other fragments/activities if needed.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private final TextView[] dataViews = new TextView[6];
    private Button uploadButton;
    private TrackingService trackingService;
    private boolean isBound;

    // Receiver that listens for when the upload task is finished
    private final BroadcastReceiver bReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, @NonNull Intent intent) {
            if (intent.getAction().equals(UploadService.getAction())) {
                uploadComplete(intent);
            }
        }
    };

    // Provides callbacks for when TrackingService is bound/unbound
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            // Get service object
            TrackingService.LocalBinder binder = (TrackingService.LocalBinder) iBinder;
            trackingService = binder.getService();
            isBound = true;

            // Set switch state
            SwitchCompat mySwitch = findViewById(R.id.tracking_switch);
            mySwitch.setChecked(trackingService.getTracking());
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            // Only called if unbound prematurely (not using unbind())
            isBound = false;
        }
    };

    /**
     * Called when the activity is first launched. Setup UI elements and ViewModel.
     *
     * @param savedInstanceState - the saved app state to load
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "Created!");

        // Collect Text boxes to display data
        dataViews[0] = findViewById(R.id.dateTextView);
        dataViews[1] = findViewById(R.id.bumpTextView);
        dataViews[2] = findViewById(R.id.startTextView);
        dataViews[3] = findViewById(R.id.endTextView);
        dataViews[4] = findViewById(R.id.distTextView);
        dataViews[5] = findViewById(R.id.speedTextView);

        // Setup ViewModel and live data
        TrackingViewModel mViewModel = new ViewModelProvider(this).get(TrackingViewModel.class);
        mViewModel.getStart().observe(this, this::setStartText);
        mViewModel.getEnd().observe(this, this::setEndText);
        mViewModel.getDist().observe(this, this::setDistText);
        mViewModel.getSpeed().observe(this, this::setSpeedText);
        mViewModel.getBumpiness().observe(this, this::setBumpText);
        mViewModel.update();

        // Request location permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        // Handle Tracking switch
        SwitchCompat mySwitch = findViewById(R.id.tracking_switch);
        mySwitch.setOnCheckedChangeListener((compoundButton, isActive) -> {
            if (isActive && !trackingService.getTracking()) {
                // Start TrackingService
                Intent intent = new Intent(getApplicationContext(), TrackingService.class);
                getApplicationContext().startService(intent);
            } else if (!isActive && trackingService.getTracking()) {
                trackingService.disableTracking();
            }
        });

        // Handle Upload Button
        uploadButton = findViewById(R.id.upload_button);
        uploadButton.setOnClickListener(view -> {
            uploadButton.setEnabled(false);

            // Start UploadService
            Intent intent = new Intent(getApplicationContext(), UploadService.class);
            getApplicationContext().startService(intent);
        });
    }

    /**
     * Called whenever the app enters the foreground.
     * Starts tracking data and listening for broadcasts.
     */
    @Override
    protected void onResume() {
        super.onResume();

        if (!isBound) {
            // Bind TrackingService
            Intent intent = new Intent(getApplicationContext(), TrackingService.class);
            getApplicationContext().bindService(intent, connection, Context.BIND_AUTO_CREATE);
        }

        LocalBroadcastManager.getInstance(getApplicationContext())
                .registerReceiver(bReceiver, new IntentFilter(UploadService.getAction()));
        uploadButton.setEnabled(true);
    }

    /**
     * Called whenever the app leaves the foreground.
     * Stops tracking data and listening for broadcasts.
     */
    @Override
    protected void onPause() {
        super.onPause();

        if (isBound) {
            // Unbind TrackingService
            getApplicationContext().unbindService(connection);
            isBound = false;
        }

        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(bReceiver);
    }

    private void setStartText(Date date) {
        if (date != null) {
            String format = "MMMM d, yyyy";
            DateFormat df = new SimpleDateFormat(format, Locale.getDefault());
            dataViews[0].setText(df.format(date));

            format = "hh:mm a";
            df = new SimpleDateFormat(format, Locale.getDefault());
            dataViews[2].setText(df.format(date));
        }
    }

    private void setEndText(Date date) {
        if (date != null) {
            final String format = "hh:mm a";
            final DateFormat df = new SimpleDateFormat(format, Locale.getDefault());
            dataViews[3].setText(df.format(date));
        }
    }

    private void setBumpText(Double bumpiness) {
        if (bumpiness != null) {
            dataViews[1].setText(String.format(Locale.getDefault(), "%.2f m/s^2", bumpiness));
        }
    }

    private void setDistText(Double dist) {
        if (dist != null) {
            dataViews[4].setText(String.format(Locale.getDefault(), "%.2f km", dist));
        }
    }

    private void setSpeedText(Double speed) {
        if (speed != null) {
            dataViews[5].setText(String.format(Locale.getDefault(), "%.2f km/h", speed));
        }
    }

    /**
     * Display the appropriate message when UploadService completes.
     * Called when a broadcast is received from the UploadService.
     *
     * @param intent - has extras:
     *               success - (boolean) whether upload completed successfully
     *               total - (int) number of rows attempted to upload
     *               uploaded - (int) number of rows uploaded successfully
     *               message - (String) message to display if unsuccessful
     */
    private void uploadComplete(@NonNull Intent intent) {
        boolean success = intent.getBooleanExtra(getString(R.string.success_key), false);
        if (success) {
            // Display number of rows uploaded
            int total = intent.getIntExtra(getString(R.string.total_key), 0);
            int uploaded = intent.getIntExtra(getString(R.string.uploaded_key), 0);
            String toast_text = String.format(Locale.getDefault(), getString(R.string.uploaded_template), uploaded, total);
            Toast.makeText(getApplicationContext(), toast_text, Toast.LENGTH_SHORT).show();
        } else {
            // Display error message
            String message = intent.getStringExtra(getString(R.string.message_key));
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        }
        uploadButton.setEnabled(true);
    }
}