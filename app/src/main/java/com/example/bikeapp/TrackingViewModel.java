package com.example.bikeapp;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.bikeapp.db.Segment;
import com.example.bikeapp.db.TripSummary;

import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains the business logic for the UI.
 * Retrieves data from the repository and provides it to the MainActivity.
 */
public class TrackingViewModel extends AndroidViewModel {

    private final DataRepository mRepository;
    private List<Integer> trips;
    //private int tripID;
    //private int maxID;
    //private int minID = 1;
    private int tripIndex = -1;

    public TrackingViewModel(Application application) {
        super(application);
        BikeApp app = (BikeApp) application;
        mRepository = app.getRepository();
    }

    // ************************** LiveData Getter Methods ***********************************
    //LiveData<Integer> getMinTrip() {return mRepository.getMinTrip();}

    LiveData<TripSummary> getTripSummary() {
        return mRepository.getTripSummary();
    }

    LiveData<List<Integer>> getTrips() {return mRepository.getTrips();}

    /**
     * Update the trip summary shown in the interface
     */
    void update() {
        if (trips != null && trips.size() > 0 && tripIndex > -1) {
            mRepository.update(trips.get(tripIndex));
        }
    }

    /**
     * Update the minimum trip value that can be shown
     * @param minTrip - the tripID of the first trip in the database
     */
//    void setMinTrip(Integer minTrip) {
//        if (minTrip > 1) {
//            minID = minTrip;
//        }
//    }

    /**
     * Decrement the trip shown in the interface.
     * Can only decrement if there is an earlier trip in the database.
     * Update the trip summary shown to the interface.
     * @return true if updated successfully, false otherwise
     */
    boolean decrement() {
        if (tripIndex > 0) {
            tripIndex--;
            update();
            return true;
        }
        return false;
    }

    /**
     * Increment the trip shown in the interface.
     * Can only increment if there is another trip after the current one.
     * Update the trip summary shown to the interface
     * @return true if updated successfully, false otherwise
     */
    boolean increment() {
        if (tripIndex < trips.size() - 1) {
            tripIndex++;
            update();
            return true;
        }
        return false;
    }

    /**
     * Increment the maximum trip that can be shown.
     * Called when a new trip is added.
     */
//    void incrementMax() {
//        maxID++;
//    }

    /**
     * Get the current tripID and assign it to tripID and maxID.
     * Accesses the value from the shared preferences file.
     */
//    private void getTripID() {
//        Application app = getApplication();
//        String PREFS = app.getString(R.string.preference_file_key);
//        SharedPreferences sharedPref = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
//        tripID = sharedPref.getInt(app.getString(R.string.prefs_trip_key), 0);
//        maxID = tripID;
//    }

    /**
     * Update the minimum tripID present in the database
     */
    public void updateMinTrip() {
        mRepository.updateMinTrip();
    }

    /**
     * Produce a list of Polylines from Segments of the trip.
     * @param segments - the segments of the given trip
     * @return - the polylines to draw to the map
     */
    public List<Polyline> getLines(@NonNull List<Segment> segments) {
        // Get the maximum RMS z acceleration over a segment
        double max = 0;
        for (int i = 0; i < segments.size(); i++) {
            double value = segments.get(i).getRmsZAccel();
            if (value > max) {
                max = value;
            }
        }

        // Convert the segments into Polylines
        List<Polyline> lines = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            Polyline line = segment.toPolyline();
            line.setWidth(10f);
            line.setColor(getColor(segment.getRmsZAccel(), max));
            lines.add(line);
        }
        return lines;
    }

    /**
     * Return the integer color value that a segment should be colored.
     * Uses a linear gradient with green as 0 and red as the maximum value.
     * @param value - the value used to determine the color
     * @param max - the maximum value for the gradient
     * @return - the integer color value
     */
    private int getColor(double value, double max) {
        double avg = Math.min(value, max);
        int color = (int)(avg * 510 / max);
        int red = 255;
        int green = 255;
        if (color > 255) {
            green = 510 - color;
        } else {
            red = color;
        }

        String colorString = String.format("#%02X%02X00", red, green);
        //Log.d("Color", colorString);
        return Color.parseColor(colorString);
    }

    void setTrips(List<Integer> trips) {
        this.trips = trips;
        if (tripIndex == -1) {
            tripIndex = trips.size() - 1;
            update();
        }
    }
}
