package com.example.bikeapp;

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
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.bikeapp.db.AccelerometerData;
import com.example.bikeapp.db.LocationData;

import java.util.Locale;

/**
 * MainActivity Class
 * Holds the UI elements for the app. Created on app startup.
 * Binds to and starts Services and launches other fragments/activities if needed.
 *
 * @author Kai Luedemann
 * @version 1.0
 * @since 2022-06-13
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private TextView[] dataViews;
    private TextView[] locationViews;
    private Button uploadButton;
    private TrackingService trackingService;
    private boolean isBound;

    private final BroadcastReceiver bReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(UploadService.getAction())) {
                boolean success = intent.getBooleanExtra("success", false);
                if (success) {
                    int total = intent.getIntExtra("total", 0);
                    int uploaded = intent.getIntExtra("uploaded", 0);
                    String toast_text = String.format(Locale.getDefault(), "Uploaded rows: %d/%d", uploaded, total);
                    Toast.makeText(MainActivity.this, toast_text, Toast.LENGTH_SHORT).show();
                } else {
                    String message = intent.getStringExtra("message");
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                }
                uploadButton.setEnabled(true);
            }
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            TrackingService.LocalBinder binder = (TrackingService.LocalBinder) iBinder;
            trackingService = binder.getService();
            isBound = true;

            SwitchCompat mySwitch = findViewById(R.id.tracking_switch);
            mySwitch.setChecked(trackingService.getTracking());
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            isBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TrackingViewModel mViewModel = new ViewModelProvider(this).get(TrackingViewModel.class);
        mViewModel.getLoc().observe(this, this::setLocationText);
        mViewModel.getAccel().observe(this, this::setAccelText);

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

        // Handle Tracking switch
        SwitchCompat mySwitch = findViewById(R.id.tracking_switch);
        mySwitch.setOnCheckedChangeListener((compoundButton, b) -> {

            if (b && !trackingService.getTracking()) {
                Intent intent = new Intent(getApplicationContext(), TrackingService.class);
                getApplicationContext().startService(intent);
            } else if (!b && trackingService.getTracking()){
                trackingService.disableTracking();
            }
        });

        // Handle Upload Button
        uploadButton = findViewById(R.id.upload_button);
        uploadButton.setOnClickListener(view -> {
            // Get data from local DB and upload to server
            uploadButton.setEnabled(false);
            Intent intent = new Intent(getApplicationContext(), UploadService.class);
            getApplicationContext().startService(intent);
        });
    }

    protected void onResume() {
        super.onResume();

        LocalBroadcastManager.getInstance(this).registerReceiver(bReceiver, new IntentFilter(UploadService.getAction()));

        if (!isBound) {
            Intent intent = new Intent(getApplicationContext(), TrackingService.class);
            getApplicationContext().bindService(intent, connection, Context.BIND_AUTO_CREATE);
        }

        uploadButton.setEnabled(true);
    }

    protected void onPause() {
        // Stop all background processes upon app being paused
        super.onPause();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(bReceiver);

        if (isBound) {
            getApplicationContext().unbindService(connection);
            isBound = false;
        }
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
}