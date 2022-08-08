package com.bikevibes.bikeapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.bikevibes.bikeapp.db.TripSummary;

import org.osmdroid.config.Configuration;
import org.osmdroid.library.BuildConfig;
import org.osmdroid.tileprovider.tilesource.ThunderforestTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
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
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.main_toolbar));

        initializeMap();
        collectDataViews();
        initializeViewModel();
        requestPermissions();
        initializeTrackingSwitch();
        initializeUploadButton();
    }

    /**
     * Called whenever the app enters the foreground.
     * Starts tracking data and listening for broadcasts.
     */
    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();

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
        map.onPause();

        if (isBound) {
            // Unbind TrackingService
            getApplicationContext().unbindService(connection);
            isBound = false;
        }

        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(bReceiver);
    }

    /**
     * Inflate the toolbar menu.
     * @param menu - the menu used in the toolbar
     * @return - true if the menu is displayed, false otherwise
     */
    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    /**
     * Called when an item in the options menu (toolbar) is selected.
     * Performs the desired action (swapping current trip).
     * @param item - the menu item that has been selected
     * @return - true if the action is consumed, false if the rest of the UI should handle the input
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_next) {
            // Display next trip
            if (!viewModel.increment()) {
                Toast.makeText(getApplicationContext(), getString(R.string.last_trip), Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (item.getItemId() == R.id.action_previous) {
            // Display previous trip
            if (!viewModel.decrement()) {
                Toast.makeText(getApplicationContext(), getString(R.string.first_trip), Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (item.getItemId() == R.id.action_settings) {
            // Open settings menu
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Initialize the OSMdroid map when the activity is created.
     * Set the tile source, add an overlay, and configure settings.
     */
    private void initializeMap() {
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        // Initialize map
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);
        map = findViewById(R.id.mapView);
        map.setTileSource(new ThunderforestTileSource(ctx, ThunderforestTileSource.NEIGHBOURHOOD));

        // Setup map overlay
        CopyrightOverlay overlay = new CopyrightOverlay(ctx);
        overlay.setAlignRight(true);
        overlay.setTextColor(getResources().getColor(android.R.color.tab_indicator_text));
        map.getOverlays().add(overlay);

        // Configure map settings
        map.setMultiTouchControls(true);
        map.setTilesScaledToDpi(true);
        setMapZoom(10.0);
        setMapCenter(53.5351, -113.4938);
    }

    /**
     * Collect the data views from their IDs.
     */
    private void collectDataViews() {
        // Collect Text boxes to display data
        dataViews[0] = findViewById(R.id.dateTextView);
        dataViews[1] = findViewById(R.id.bumpTextView);
        dataViews[2] = findViewById(R.id.startTextView);
        dataViews[3] = findViewById(R.id.endTextView);
        dataViews[4] = findViewById(R.id.distTextView);
        dataViews[5] = findViewById(R.id.speedTextView);
    }

    /**
     * Get the view model and observe trip LiveData.
     */
    private void initializeViewModel() {
        // Setup ViewModel and live data
        viewModel = new ViewModelProvider(this).get(TrackingViewModel.class);
        viewModel.getTripSummary().observe(this, this::updateTrip);
        viewModel.getTrips().observe(this, viewModel::setTrips);
    }

    /**
     * Request permissions if they have not been granted.
     */
    private void requestPermissions() {
        final String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION};

        // Check which permissions have not been granted
        ArrayList<String> toRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(permission);
            }
        }

        // Request all of the permissions not yet granted, if any
        if (toRequest.size() > 0) {
            final int REQUEST_CODE = 1;
            ActivityCompat.requestPermissions(this, toRequest.toArray(new String[0]), REQUEST_CODE);
        }
    }

    /**
     * Create a click listener for the upload button.
     * Start the upload service and set button to enabled.
     */
    private void initializeUploadButton() {
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
     * Create a change listener for tracking switch.
     * Start tracking when enabled and disable tracking when disabled.
     */
    private void initializeTrackingSwitch() {
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
    }

    /**
     * Update the corresponding UI elements when the current trip is changed
     * @param trip - the representation of the current trip
     */
    private void updateTrip(TripSummary trip) {
        if (trip != null) {
            BikeApp app = (BikeApp) getApplication();

            // Generate polylines and update the map
            app.getExecutors().execute(() -> {
                List<Polyline> lines = viewModel.getLines(trip.getSegments());
                map.post(() -> {
                    setMapLines(lines);
                    setMapZoom(trip.getZoom());
                    setMapCenter(trip.getCenterLat(), trip.getCenterLon());
                });
            });

            // Update the text fields
            setStartText(trip.getStart());
            setEndText(trip.getEnd());
            setDistText(trip.getDist());
            setSpeedText(trip.getSpeed());
            setBumpText(trip.getBumpiness());
        } else {
            resetTrip();
        }
    }

    /**
     * Reset trip UI elements if there is none.
     * Called when the user deletes their data.
     */
    private void resetTrip() {
        // Reset text
        final String UNKNOWN_TEXT = getString(R.string.unknown_text);
        for (TextView dataView : dataViews) {
            dataView.setText(UNKNOWN_TEXT);
        }

        // Reset map
        setMapLines(new ArrayList<>());
        setMapZoom(10.0);
        setMapCenter(53.5351, -113.4938);
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

    /**
     * Update the text for date and start time.
     * @param date - the Date object from the first accelerometer reading in the trip
     */
    private void setStartText(Date date) {
        // Set Date
        String format = "MMM d, yyyy";
        DateFormat df = new SimpleDateFormat(format, Locale.getDefault());
        dataViews[0].setText(df.format(date));

        // Set Start Time
        format = "hh:mm a";
        df = new SimpleDateFormat(format, Locale.getDefault());
        dataViews[2].setText(df.format(date));
    }

    /**
     * Update the text for end time.
     * @param date - the Date object from the last accelerometer reading in the trip.
     */
    private void setEndText(Date date) {
        final String format = "hh:mm a";
        final DateFormat df = new SimpleDateFormat(format, Locale.getDefault());
        dataViews[3].setText(df.format(date));
    }

    /**
     * Update the text for the bumpiness score.
     * @param bumpiness - the RMS of vertical acceleration over the trip
     */
    private void setBumpText(Double bumpiness) {
        dataViews[1].setText(String.format(Locale.getDefault(), "%.2f m/s^2", bumpiness));
    }

    /**
     * Update the text for the trip distance.
     * @param dist - the distance of the trip in km
     */
    private void setDistText(Double dist) {
        dataViews[4].setText(String.format(Locale.getDefault(), "%.2f km", dist));
    }

    /**
     * Update the text for the average speed
     * @param speed - the avg speed over the trip
     */
    private void setSpeedText(Double speed) {
        dataViews[5].setText(String.format(Locale.getDefault(), "%.2f km/h", speed));
    }

    /**
     * Update the zoom level of the map.
     * @param zoom - the double valued zoom level to change to
     */
    private void setMapZoom(Double zoom) {
        map.getController().setZoom(zoom);
    }

    /**
     * Update the center position of the map
     * @param lat - the latitude that the map should be centered to
     * @param lon - the longitude that the map should be centered to
     */
    private void setMapCenter(double lat, double lon) {
        map.getController().setCenter(new GeoPoint(lat, lon));
    }

    /**
     * Clear the current lines from the map and draw new ones.
     * @param lines - the new lines to be drawn to the map
     */
    private void setMapLines(@NonNull List<Polyline> lines) {
        map.getOverlayManager().clear();
        for (int i = 0; i < lines.size(); i++) {
            map.getOverlayManager().add(lines.get(i));
        }
    }
}