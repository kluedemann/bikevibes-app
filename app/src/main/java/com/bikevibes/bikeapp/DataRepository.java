package com.bikevibes.bikeapp;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.bikevibes.bikeapp.db.AccelerometerData;
import com.bikevibes.bikeapp.db.AppDatabase;
import com.bikevibes.bikeapp.db.DataInstance;
import com.bikevibes.bikeapp.db.LocationData;
import com.bikevibes.bikeapp.db.Segment;
import com.bikevibes.bikeapp.db.TrackingDao;
import com.bikevibes.bikeapp.db.TripSummary;
import com.bikevibes.bikeapp.db.TripSurface;

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
     * Return all accelerometer readings with a tripID less than or equal to maxTrip.
     * WARNING: This method CANNOT be called from the Main/UI thread
     * @param maxTrip - the maximum trip ID
     * @return - a list of AccelerometerData readings
     */
    List<AccelerometerData> getAccels(int maxTrip) {
        return myDao.getAccList(maxTrip);
    }

    /**
     * Return all GPS readings with a tripID less than or equal to maxTrip.
     * WARNING: This method CANNOT be called from the Main/UI thread
     * @param maxTrip - the maximum trip ID
     * @return - a list of LocationData instances
     */
    List<LocationData> getLocs(int maxTrip) {
        return myDao.getLocList(maxTrip);
    }

    List<TripSurface> getSurfaces(int maxTrip) {return myDao.getSurfaceList(maxTrip);}


    // ************************* LiveData Getter Methods ************************************
    LiveData<TripSummary> getTripSummary() {
        return trip;
    }

    LiveData<List<Integer>> getTrips() {
        return myDao.getTrips();
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
        double maxLat = Math.max(myDao.getMaxLat(tripID), firstLoc.getLatitude());
        double minLat = Math.min(myDao.getMinLat(tripID), firstLoc.getLatitude());
        double maxLon = Math.max(myDao.getMaxLon(tripID), firstLoc.getLongitude());
        double minLon = Math.min(myDao.getMinLon(tripID), firstLoc.getLongitude());

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

    /**
     * Delete all records from the local database and clear the current trip.
     */
    public void deleteAll() {
        AppDatabase.getExecutor().execute(() -> {
            myDao.deleteAllAccel();
            myDao.deleteAllLoc();
            myDao.deleteAllSegments();
            myDao.deleteAllSurfaces();
            trip.postValue(null);
        });
    }

    /**
     * Generate the segments and insert them into the database.
     * Blackout the raw data values within the blackout radius.
     * @param tripID - the trip ID to process
     * @param blackout_radius - the radius around the start and end points to remove
     */
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

    /**
     * Remove the raw data within the blackout radius around the start and end points of a trip.
     * It removes all data until a timestamp with a location recorded outside of the radius.
     * @param radius - the distance around the start/end points that will be removed
     * @param tripSegs - the Segments that compose of the trip
     * @param tripID - the trip ID
     */
    private void blackoutData(int radius, @NonNull List<Segment> tripSegs, int tripID) {
        if (tripSegs.size() < 3) {
            myDao.delAccGt(tripID, new Date(0));
            myDao.delLocGt(tripID, new Date(0));
            myDao.deleteTripSurface(tripID);
            return;
        }
        Date minTS = getBlackoutStart(tripSegs, radius);
        Date maxTS = getBlackoutEnd(tripSegs, radius);
        deleteBlackoutData(tripID, minTS, maxTS);
    }

    /**
     * Delete the given data instance from the database.
     * @param data - the accelerometer or GPS recording to delete
     */
    void delete(DataInstance data) {
        AppDatabase.getExecutor().execute(() -> {
            if (data instanceof LocationData) {
                myDao.deleteLocation((LocationData) data);
            } else if (data instanceof AccelerometerData) {
                myDao.deleteAccel((AccelerometerData) data);
            } else if (data instanceof TripSurface) {
                myDao.deleteSurface((TripSurface) data);
            }
        });
    }

    /**
     * Return the timestamp before which to delete.
     * Usually the timestamp of the first location outside of the blackout radius.
     * Adds one if it is the last recorded location, so it is also deleted.
     * @param tripSegs - the trip segments
     * @param radius - the radius to delete in m
     * @return minTS - the Date before which to delete records
     */
    private Date getBlackoutStart(@NonNull List<Segment> tripSegs, double radius) {
        LocationData firstLoc = tripSegs.get(0).getLoc1();
        LocationData current = tripSegs.get(0).getLoc2();
        int i = 1;
        while (firstLoc.getDist(current) * 1000 < radius && i < tripSegs.size()) {
            current = tripSegs.get(i).getLoc2();
            i++;
        }
        Date minTS = current.getTimestamp();
        if (i == tripSegs.size()) {
            minTS = new Date(minTS.getTime() + 1);
        }
        return minTS;
    }

    /**
     * Return the timestamp after which to delete.
     * Usually the timestamp of the last location outside of the blackout radius.
     * Subtracts one if it is the first recorded location, so it is also deleted.
     * @param tripSegs - the trip segments
     * @param radius - the radius to delete in m
     * @return maxTS - the Date after which to delete records
     */
    private Date getBlackoutEnd(@NonNull List<Segment> tripSegs, double radius) {
        LocationData lastLoc = tripSegs.get(tripSegs.size() - 1).getLoc2();
        LocationData current = tripSegs.get(tripSegs.size() - 1).getLoc1();
        int i = tripSegs.size() - 2;
        while (lastLoc.getDist(current) * 1000 < radius && i >= 0) {
            current = tripSegs.get(i).getLoc1();
            i--;
        }
        Date maxTS = current.getTimestamp();
        if (i < 0) {
            maxTS = new Date(maxTS.getTime() - 1);
        }
        return maxTS;
    }

    /**
     * Delete the trip data before minTS and after maxTS.
     * If they are equal, delete that point as well.
     * @param tripID - the trip to delete from
     * @param minTS - the Date before which to delete
     * @param maxTS - the Date after which to delete
     */
    private void deleteBlackoutData(int tripID, Date minTS, Date maxTS) {
        if (minTS == maxTS) {
            maxTS = new Date(maxTS.getTime() - 1);
        }
        myDao.delAccLt(tripID, minTS);
        myDao.delLocLt(tripID, minTS);
        myDao.delAccGt(tripID, maxTS);
        myDao.delLocGt(tripID, maxTS);

        if (myDao.countLocs(tripID) == 0) {
            myDao.deleteTripSurface(tripID);
        }
    }

    public void insertTrip(TripSurface trip) {
        AppDatabase.getExecutor().execute(() -> myDao.insertSurface(trip));
    }

    public void updateTrip(TripSurface trip) {
        AppDatabase.getExecutor().execute(() -> myDao.updateSurface(trip));
    }
}
