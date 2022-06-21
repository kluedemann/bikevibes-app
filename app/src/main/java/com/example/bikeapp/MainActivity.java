package com.example.bikeapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
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
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.ThunderforestTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polyline;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
    private MapView map = null;
    private TrackingViewModel viewModel;

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
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        setContentView(R.layout.activity_main);
        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(myToolbar);

        Log.d(TAG, "Created!");

        // Collect Text boxes to display data
        dataViews[0] = findViewById(R.id.dateTextView);
        dataViews[1] = findViewById(R.id.bumpTextView);
        dataViews[2] = findViewById(R.id.startTextView);
        dataViews[3] = findViewById(R.id.endTextView);
        dataViews[4] = findViewById(R.id.distTextView);
        dataViews[5] = findViewById(R.id.speedTextView);

        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);
        map = (MapView) findViewById(R.id.mapView);
        map.setTileSource(new ThunderforestTileSource(ctx, ThunderforestTileSource.NEIGHBOURHOOD));
        map.setMultiTouchControls(true);
        map.setTilesScaledToDpi(true);
        //map.post(this::updateMap);

        // Setup ViewModel and live data
        viewModel = new ViewModelProvider(this).get(TrackingViewModel.class);
        viewModel.getStart().observe(this, this::setStartText);
        viewModel.getEnd().observe(this, this::setEndText);
        viewModel.getDist().observe(this, this::setDistText);
        viewModel.getSpeed().observe(this, this::setSpeedText);
        viewModel.getBumpiness().observe(this, this::setBumpText);
        viewModel.getZoom().observe(this, this::updateZoom);
        viewModel.getCenter().observe(this, this::updateCenter);
        viewModel.getLines().observe(this, this::updateLines);
        viewModel.getMinTrip().observe(this, viewModel::updateMinTrip);
        viewModel.update();

        // Request location permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
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
                viewModel.incrementMax();
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

        map.onResume();
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
        map.onPause();
    }

    /**
     * Update the text for date and start time.
     * @param date - the Date object from the first accelerometer reading in the trip
     */
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

    /**
     * Update the text for end time.
     * @param date - the Date object from the last accelerometer reading in the trip.
     */
    private void setEndText(Date date) {
        if (date != null) {
            final String format = "hh:mm a";
            final DateFormat df = new SimpleDateFormat(format, Locale.getDefault());
            dataViews[3].setText(df.format(date));
        }
    }

    /**
     * Update the text for the bumpiness score.
     * @param bumpiness - the RMS of vertical acceleration over the trip
     */
    private void setBumpText(Double bumpiness) {
        if (bumpiness != null) {
            dataViews[1].setText(String.format(Locale.getDefault(), "%.2f m/s^2", bumpiness));
        }
    }

    /**
     * Update the text for the trip distance.
     * @param dist - the distance of the trip in km
     */
    private void setDistText(Double dist) {
        if (dist != null) {
            dataViews[4].setText(String.format(Locale.getDefault(), "%.2f km", dist));
        }
    }

    /**
     * Update the text for the average speed
     * @param speed - the avg speed over the trip
     */
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

    private void updateZoom(Double zoom) {
        map.getController().setZoom(zoom);
    }

    private void updateCenter(GeoPoint center) {
        map.getController().setCenter(center);
    }

    private void updateLines(List<Polyline> lines) {
        map.getOverlayManager().clear();
        for (int i = 0; i < lines.size(); i++) {
            map.getOverlayManager().add(lines.get(i));
        }
    }

    private void updateMap() {
        IMapController mapController = map.getController();
        mapController.setZoom(12f);
        GeoPoint startPoint = new GeoPoint(53.5351, -113.4938);
        mapController.setCenter(startPoint);

        map.getOverlayManager().clear();
        List<GeoPoint> geoPoints = new ArrayList<>();
        geoPoints.add(new GeoPoint(53.52, -113.51));
        geoPoints.add(new GeoPoint(53.55, -113.46));
        Polyline line = new Polyline();
        line.setPoints(geoPoints);
        line.setColor(Color.parseColor("#FF00FF00"));
        line.setWidth(5f);
        map.getOverlayManager().add(line);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_next) {
            if (!viewModel.increment()) {
                Toast.makeText(getApplicationContext(), getString(R.string.last_trip), Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (item.getItemId() == R.id.action_previous) {
            if (!viewModel.decrement()) {
                Toast.makeText(getApplicationContext(), getString(R.string.first_trip), Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}