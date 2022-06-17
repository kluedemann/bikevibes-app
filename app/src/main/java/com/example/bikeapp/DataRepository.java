package com.example.bikeapp;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.bikeapp.db.AccelerometerData;
import com.example.bikeapp.db.AppDatabase;
import com.example.bikeapp.db.LocationData;
import com.example.bikeapp.db.TrackingDao;

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
    private final MutableLiveData<Date> start = new MutableLiveData<>();
    private final MutableLiveData<Date> end = new MutableLiveData<>();
    private final MutableLiveData<Double> dist = new MutableLiveData<>();
    private final MutableLiveData<Double> speed = new MutableLiveData<>();
    private final MutableLiveData<Double> bumpiness = new MutableLiveData<>();

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

    void insertAccelBatch(List<AccelerometerData> accelList) {
        AppDatabase.getExecutor().execute(() -> myDao.insertAccelBatch(accelList));
    }

    void insertLocBatch(List<LocationData> locList) {
        AppDatabase.getExecutor().execute(() -> myDao.insertLocBatch(locList));
    }

    List<AccelerometerData> getAccelList(long minTime) {
        return myDao.getAccel(minTime);
    }

    List<LocationData> getLocList(long minTime) {
        return myDao.getLocation(minTime);
    }

    long getMaxTime() {
        long accTime = myDao.getMaxAccelTime();
        long locTime = myDao.getMaxLocTime();
        return Math.max(accTime, locTime);
    }

    LiveData<Date> getStart() {
        return start;
    }

    LiveData<Date> getEnd() {
        return end;
    }

    LiveData<Double> getDist() {
        return dist;
    }

    LiveData<Double> getSpeed() {
        return speed;
    }

    LiveData<Double> getBumpiness() {
        return bumpiness;
    }

    /**
     * Update the LiveData objects with the trip summary information
     * corresponding to the given tripID.
     * @param tripID - the trip to get information about
     */
    void update(int tripID) {
        AppDatabase.getExecutor().execute(() -> {
            Date tempStart = new Date(myDao.getTripStart(tripID));
            start.postValue(tempStart);
            Date tempEnd = new Date(myDao.getTripEnd(tripID));
            end.postValue(tempEnd);
            double tempDist = getDistance(myDao.getTripLocs(tripID));
            dist.postValue(tempDist);
            speed.postValue(getAvgSpeed(tempStart.getTime(), tempEnd.getTime(), tempDist));
            bumpiness.postValue(Math.sqrt(myDao.getMSAccel(tripID)));
        });
    }

    /**
     * Get the total distance travelled in the trip.
     * @param locs - list of location instances
     * @return dist - the distance travelled in km
     */
    private double getDistance(List<LocationData> locs) {
        if (locs.size() < 2) {
            return 0;
        }
        double dist = 0;
        LocationData prev = locs.get(0);
        LocationData current;
        for (int i = 1; i < locs.size(); i++) {
            current = locs.get(i);
            dist += getPairDist(current, prev);
            prev = current;
        }
        return dist;
    }

    /**
     * Get the distance between a pair of location instances.
     * Uses the Haversine formula to get the Great Circle Distance.
     * @param loc1 - the first location point
     * @param loc2 - the second location point
     * @return the distance between them in km
     */
    private double getPairDist(LocationData loc1, LocationData loc2) {
        final int r = 6371; // Earth's radius
        double dLat = Math.toRadians(loc2.latitude - loc1.latitude);
        double dLon = Math.toRadians(loc2.longitude - loc1.longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.sin(dLon / 2) * Math.sin(dLon / 2)
                * Math.cos(Math.toRadians(loc1.latitude)) * Math.cos(Math.toRadians(loc2.latitude));
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        //Log.d("Distance", String.format(Locale.getDefault(), "%f, %f, %f", dLat, dLon, r*c));
        return r * c;
    }

    private double getAvgSpeed(long startTime, long endTime, double dist) {
        double hours = (endTime - startTime) / (1000 * 60 * 60f);
        if (hours != 0) {
            //Log.d("Speed", String.format(Locale.getDefault(), "%d, %d, %f", startTime, endTime, dist));
            return dist / hours;
        }
        return 0;
    }
}
