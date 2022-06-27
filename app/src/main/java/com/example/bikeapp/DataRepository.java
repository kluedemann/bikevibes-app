package com.example.bikeapp;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.bikeapp.db.AccelerometerData;
import com.example.bikeapp.db.AppDatabase;
import com.example.bikeapp.db.DataInstance;
import com.example.bikeapp.db.LocationData;
import com.example.bikeapp.db.Segment;
import com.example.bikeapp.db.TrackingDao;
import com.example.bikeapp.db.TripSummary;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Provides access to the database for app components.
 * Contains a singleton repository instance, data access object, and
 * some current data objects.
 */
public class DataRepository {
    private static volatile DataRepository instance;

    private final TrackingDao myDao;
    private final MutableLiveData<Integer> minTripID = new MutableLiveData<>();
    private final MutableLiveData<TripSummary> trip = new MutableLiveData<>();

    DataRepository(@NonNull final AppDatabase database) {
        myDao = database.myDao();
    }

    /**
     * Get the singleton repository instance or create it if needed.
     * @param database - the database that the repository accesses
     * @return instance - the repository instance
     */
    public static DataRepository getInstance(final AppDatabase database) {
        if (instance == null) {
            synchronized (DataRepository.class) {
                if (instance == null) {
                    instance = new DataRepository(database);
                }
            }
        }
        return instance;
    }

    /**
     * Insert a list of accelerometer readings into the database.
     * @param accelList - the list of data instances to insert
     */
    void insertAccelBatch(List<AccelerometerData> accelList) {
        AppDatabase.getExecutor().execute(() -> myDao.insertAccelBatch(accelList));
    }

    /**
     * Insert a list of GPS readings into the database.
     * @param locList - the list of GPS data points to insert
     */
    void insertLocBatch(List<LocationData> locList) {
        AppDatabase.getExecutor().execute(() -> myDao.insertLocBatch(locList));
    }

    /**
     * Return all accelerometer readings that occurred after minTime.
     * WARNING: This method CANNOT be called from the Main/UI thread
     * @param minTime - the minimum UNIX timestamp in milliseconds
     * @return - a list of AccelerometerData readings
     */
    List<AccelerometerData> getAccelList(Date minTime) {
        return myDao.getAccel(minTime);
    }
    List<AccelerometerData> getAccels(int maxTrip) {
        return myDao.getAccList(maxTrip);
    }

    /**
     * Return all GPS readings that occurred after minTime.
     * WARNING: This method CANNOT be called from the Main/UI thread
     * @param minTime - the minimum UNIX timestamp in milliseconds
     * @return - a list of LocationData instances
     */
    List<LocationData> getLocList(Date minTime) {
        return myDao.getLocation(minTime);
    }
    List<LocationData> getLocs(int maxTrip) {
        return myDao.getLocList(maxTrip);
    }

    /**
     * Return the maximum accelerometer timestamp that currently exists in the database.
     * @return - the maximum UNIX timestamp in milliseconds
     */
    Date getAccTime() {
        return myDao.getMaxAccelTime();
    }

    /**
     * Return the maximum GPS timestamp that currently exists in the database.
     * @return - the maximum UNIX timestamp in milliseconds
     */
    Date getLocTime() {
        return myDao.getMaxLocTime();
    }

    // ************************* LiveData Getter Methods ************************************
    LiveData<Integer> getMinTrip() {
        return minTripID;
    }

    LiveData<TripSummary> getTripSummary() {
        return trip;
    }

    /**
     * Update the LiveData objects with the trip summary information
     * corresponding to the given tripID.
     * @param tripID - the trip to get information about
     */
    void update(int tripID) {
        AppDatabase.getExecutor().execute(() -> {
            List<Segment> segs = myDao.getSegments(tripID);
            if (segs.size() > 0) {
                Date start = myDao.getTripStartSeg(tripID);
                Date end = myDao.getTripEndSeg(tripID);
                double bumpiness = Math.sqrt(myDao.getAvgAccel(tripID));
                TripSummary temp = new TripSummary(tripID, segs, start, end, bumpiness);
                updateMap(temp, segs, tripID);
                trip.postValue(temp);
            }
        });
    }

    /**
     * Update the minimum tripID from the database.
     */
    public void updateMinTrip() {
        AppDatabase.getExecutor().execute(() -> minTripID.postValue(myDao.getMinTrip()));
    }


//    private void updateMap(@NonNull TripSummary trip, @NonNull List<LocationData> locs, int tripID) {
//        double maxLat = myDao.getMaxLat(tripID);
//        double minLat = myDao.getMinLat(tripID);
//        double maxLon = myDao.getMaxLon(tripID);
//        double minLon = myDao.getMinLon(tripID);
//
//        //Log.d("MAP", String.format("%f, %f, %f, %f", maxLat, minLat, maxLon, minLon));
//
//        // Calculate the map's zoom level
//        // See https://wiki.openstreetmap.org/wiki/Zoom_levels
//        double latDif = maxLat - minLat;
//        double lonDif = maxLon - minLon;
//
//        // Find the largest tile that the entire trip can fit into and add 1 level
//        int zoomLat = Math.min((int) ((-Math.log(latDif / 180) / Math.log(2)) + 0.5), 20);
//        int zoomLon = Math.min((int) ((-Math.log(lonDif / 360) / Math.log(2)) + 0.5), 20);
//
//
//        // Calculate the map's center position
//        double centerLat = (maxLat + minLat) / 2;
//        double centerLon = (maxLon + minLon) / 2;
//
//        // Update the values
//        List<Segment> segments = getSegments(locs, tripID);
//        double zoom = Math.min(zoomLat, zoomLon);
//        trip.setMap(segments, centerLat, centerLon, zoom);
//    }

    /**
     * Update the center of the map, zoom level, and lines to draw from
     * the list of location instances recorded during a given trip.
     *
     * CenterLat/Lon are set as the midpoint between the maximum and minimum lat/long coordinates.
     * Zoom level is determined by calculating the minimum tile size that will fit the
     * entire trip into a single tile. Then we add 0.5 zoom levels because multiple tiles fit into
     * the map window.
     * Segments are taken from pairs of LocationData instances. They also store the RMS of x
     * acceleration over that portion of the trip.
     *
     * @param trip - the TripSummary to be updated
     * @param segs - the list of location instances from the trip
     * @param tripID - the trip that is being considered
     */
    private void updateMap(@NonNull TripSummary trip, @NonNull List<Segment> segs, int tripID) {
        LocationData firstLoc = segs.get(0).getLoc1();
        double maxLat = Math.max(myDao.getMaxLatSeg(tripID), firstLoc.getLatitude());
        double minLat = Math.min(myDao.getMinLatSeg(tripID), firstLoc.getLatitude());
        double maxLon = Math.max(myDao.getMaxLonSeg(tripID), firstLoc.getLongitude());
        double minLon = Math.min(myDao.getMinLonSeg(tripID), firstLoc.getLongitude());

        //Log.d("MAP", String.format("%f, %f, %f, %f", maxLat, minLat, maxLon, minLon));

        // Calculate the map's zoom level
        // See https://wiki.openstreetmap.org/wiki/Zoom_levels
        double latDif = maxLat - minLat;
        double lonDif = maxLon - minLon;

        // Find the largest tile that the entire trip can fit into and add 1 level
        int zoomLat = Math.min((int) ((-Math.log(latDif / 180) / Math.log(2)) + 0.5), 20);
        int zoomLon = Math.min((int) ((-Math.log(lonDif / 360) / Math.log(2)) + 0.5), 20);


        // Calculate the map's center position
        double centerLat = (maxLat + minLat) / 2;
        double centerLon = (maxLon + minLon) / 2;

        // Update the values
        double zoom = Math.min(zoomLat, zoomLon);
        trip.setMap(segs, centerLat, centerLon, zoom);
    }

    /**
     * Get the segments of the trip from the list of location instances
     * @param locs - the list of LocationData instances
     * @return the list of segments in the trip
     */
    @NonNull
    private List<Segment> getSegments(@NonNull List<LocationData> locs, int tripID) {
        if (locs.size() < 2) {
            return new ArrayList<>();
        }
        ArrayList<Segment> segments = new ArrayList<>();
        LocationData prev = locs.get(0);
        LocationData current;
        for (int i = 1; i < locs.size(); i++) {
            current = locs.get(i);
            double rmsZAccel = Math.sqrt(myDao.getRmsZAccel(prev.getTimestamp(), current.getTimestamp()));
            double maxZAccel = myDao.getMaxZAccel(prev.getTimestamp(), current.getTimestamp());
            segments.add(new Segment(tripID, prev, current, rmsZAccel, maxZAccel));
            prev = current;
        }
        return segments;
    }

    public void deleteAll() {
        AppDatabase.getExecutor().execute(() -> {
            myDao.deleteAllAccel();
            myDao.deleteAllLoc();
            myDao.deleteAllSegments();
        });
    }

    public void createSegments(int tripID, int blackout_radius) {
        AppDatabase.getExecutor().execute(() -> {
            List<LocationData> locs = myDao.getTripLocs(tripID);
            List<Segment> tripSegs = getSegments(locs, tripID);
            myDao.insertSegments(tripSegs);

            if (blackout_radius > 0) {
                blackoutData(blackout_radius, tripSegs, tripID);
            }
        });
    }

    private void blackoutData(int radius, @NonNull List<Segment> tripSegs, int tripID) {
        if (tripSegs.size() < 3) {
            myDao.delAccGt(tripID, new Date(0));
            myDao.delLocGt(tripID, new Date(0));
            return;
        }
        LocationData firstLoc = tripSegs.get(0).getLoc1();
        LocationData current = tripSegs.get(0).getLoc2();
        int i = 1;
        while (getPairDist(firstLoc, current) * 1000 < radius && i < tripSegs.size()) {
            current = tripSegs.get(i).getLoc2();
            i++;
        }
        Date minTS = current.getTimestamp();
        if (i == tripSegs.size()) {
            minTS = new Date(minTS.getTime() + 1);
        }
        myDao.delAccLt(tripID, minTS);
        myDao.delLocLt(tripID, minTS);

        LocationData lastLoc = tripSegs.get(tripSegs.size() - 1).getLoc2();
        current = tripSegs.get(tripSegs.size() - 1).getLoc1();
        i = tripSegs.size() - 2;
        while (getPairDist(lastLoc, current) * 1000 < radius && i >= 0) {
            current = tripSegs.get(i).getLoc1();
            i--;
        }
        Date maxTS = current.getTimestamp();
        if (i < 0 || maxTS == minTS) {
            maxTS = new Date(maxTS.getTime() - 1);
        }
        myDao.delAccGt(tripID, maxTS);
        myDao.delLocGt(tripID, maxTS);

        Log.d("Repository", String.format("%d, %d", minTS.getTime(), maxTS.getTime()));
    }

    private double getPairDist(@NonNull LocationData loc1, @NonNull LocationData loc2) {
        final int r = 6371; // Earth's radius
        double dLat = Math.toRadians(loc2.getLatitude() - loc1.getLatitude());
        double dLon = Math.toRadians(loc2.getLongitude() - loc1.getLongitude());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.sin(dLon / 2) * Math.sin(dLon / 2)
                * Math.cos(Math.toRadians(loc1.getLatitude())) * Math.cos(Math.toRadians(loc2.getLatitude()));
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        //Log.d("Distance", String.format(Locale.getDefault(), "%f, %f, %f", dLat, dLon, r*c));
        return r * c;
    }

    void delete(DataInstance data) {
        AppDatabase.getExecutor().execute(() -> {
            if (data instanceof LocationData) {
                myDao.deleteLocation((LocationData) data);
            } else if (data instanceof AccelerometerData) {
                myDao.deleteAccel((AccelerometerData) data);
            }
        });
    }

    LiveData<List<Integer>> getTrips() {
        return myDao.getTrips();
    }
}
