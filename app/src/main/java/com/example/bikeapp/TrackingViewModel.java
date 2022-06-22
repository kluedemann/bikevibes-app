package com.example.bikeapp;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polyline;

import java.util.Date;
import java.util.List;

/**
 * Contains the business logic for the UI.
 * Retrieves data from the repository and provides it to the MainActivity.
 */
public class TrackingViewModel extends AndroidViewModel {

    private final DataRepository mRepository;
    private int tripID;
    private int maxID;
    private int minID = 1;

    public TrackingViewModel(Application application) {
        super(application);
        BikeApp app = (BikeApp) application;
        mRepository = app.getRepository();
        getTripID();
    }

    // ************************** LiveData Getter Methods ***********************************
    LiveData<Date> getStart() {
        return mRepository.getStart();
    }

    LiveData<Date> getEnd() {
        return mRepository.getEnd();
    }

    LiveData<Double> getDist() {
        return mRepository.getDist();
    }

    LiveData<Double> getSpeed() {
        return mRepository.getSpeed();
    }

    LiveData<Double> getBumpiness() {
        return mRepository.getBumpiness();
    }

    LiveData<Double> getZoom() {
        return mRepository.getZoom();
    }

    LiveData<GeoPoint> getCenter() {
        return mRepository.getCenter();
    }

    LiveData<List<Polyline>> getLines() {
        return mRepository.getLines();
    }

    LiveData<Integer> getMinTrip() {return mRepository.getMinTrip();}

    /**
     * Update the trip summary shown in the interface
     */
    void update() {
        if (tripID != 0) {
            mRepository.update(tripID);
        }
    }

    /**
     * Update the minimum trip value that can be shown
     * @param minTrip - the tripID of the first trip in the database
     */
    void setMinTrip(Integer minTrip) {
        if (minTrip > 1) {
            minID = minTrip;
        }
    }

    /**
     * Decrement the trip shown in the interface.
     * Can only decrement if there is an earlier trip in the database.
     * Update the trip summary shown to the interface.
     * @return true if updated successfully, false otherwise
     */
    boolean decrement() {
        if (tripID > minID) {
            tripID--;
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
        if (tripID < maxID) {
            tripID++;
            update();
            return true;
        }
        return false;
    }

    /**
     * Increment the maximum trip that can be shown.
     * Called when a new trip is added.
     */
    void incrementMax() {
        maxID++;
    }

    /**
     * Get the current tripID and assign it to tripID and maxID.
     * Accesses the value from the shared preferences file.
     */
    private void getTripID() {
        Application app = getApplication();
        String PREFS = app.getString(R.string.preference_file_key);
        SharedPreferences sharedPref = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        tripID = sharedPref.getInt(app.getString(R.string.prefs_trip_key), 0);
        maxID = tripID;
    }

    /**
     * Update the minimum tripID present in the database
     */
    public void updateMinTrip() {
        mRepository.updateMinTrip();
    }
}
