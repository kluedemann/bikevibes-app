package com.example.bikeapp;

import android.graphics.Color;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.bikeapp.db.AccelerometerData;
import com.example.bikeapp.db.AppDatabase;
import com.example.bikeapp.db.LocationData;
import com.example.bikeapp.db.TrackingDao;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polyline;

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
    private final MutableLiveData<Date> start = new MutableLiveData<>();
    private final MutableLiveData<Date> end = new MutableLiveData<>();
    private final MutableLiveData<Double> dist = new MutableLiveData<>();
    private final MutableLiveData<Double> speed = new MutableLiveData<>();
    private final MutableLiveData<Double> bumpiness = new MutableLiveData<>();
    private final MutableLiveData<Double> zoom = new MutableLiveData<>();
    private final MutableLiveData<GeoPoint> center = new MutableLiveData<>();
    private final MutableLiveData<List<Polyline>> lines = new MutableLiveData<>();

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

    LiveData<Double> getZoom() {return zoom;}

    LiveData<GeoPoint> getCenter() {return center;}

    LiveData<List<Polyline>> getLines() {
        return lines;
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
            List<LocationData> locs = myDao.getTripLocs(tripID);
            double tempDist = getDistance(locs);
            dist.postValue(tempDist);
            speed.postValue(getAvgSpeed(tempStart.getTime(), tempEnd.getTime(), tempDist));
            bumpiness.postValue(Math.sqrt(myDao.getMSAccel(tripID)));

            // Temporary values
            updateMap(locs, tripID);

        });
    }

    private void updateMap(List<LocationData> locs, int tripID) {
        if (locs.size() < 2) {
            zoom.postValue((double)12);
            center.postValue(new GeoPoint(53.5351, -113.4938));
            lines.postValue(new ArrayList<>());
            return;
        }

        double maxLat = myDao.getMaxLat(tripID);
        double minLat = myDao.getMinLat(tripID);
        double maxLon = myDao.getMaxLon(tripID);
        double minLon = myDao.getMinLon(tripID);

        //Log.d("MAP", String.format("%f, %f, %f, %f", maxLat, minLat, maxLon, minLon));

        // Calculate the map's position
        // See https://wiki.openstreetmap.org/wiki/Zoom_levels
        double latDif = maxLat - minLat;
        double lonDif = maxLon - minLon;

        // Find the largest tile that the entire trip can fit into and add 1 level
        int zoomLat = Math.min((int) (-Math.log(latDif / 180) / Math.log(2)) + 1, 20);
        int zoomLon = Math.min((int) (-Math.log(lonDif / 360) / Math.log(2)) + 1, 20);
        zoom.postValue((double) Math.min(zoomLat, zoomLon));

        double centerLat = (maxLat + minLat) / 2;
        double centerLon = (maxLon + minLon) / 2;
        center.postValue(new GeoPoint(centerLat, centerLon));

        // Calculate center position
        ArrayList<GeoPoint> points = new ArrayList<>();
        for (int i = 0; i < locs.size(); i++) {
            LocationData loc = locs.get(i);
            //Log.d("Point", String.format("%d, %f, %f", loc.getTimestamp(), loc.getLatitude(), loc.getLongitude()));
            points.add(loc.getGeoPoint());

        }

        double max = 0;
        double[] accels = new double[locs.size() - 1];
        for (int i = 0; i < locs.size() - 1; i++) {
            double value = Math.sqrt(myDao.getRMSTime(locs.get(i).getTimestamp(), locs.get(i+1).getTimestamp()));
            //Log.d("Values", String.valueOf(value));
            accels[i] = value;
            if (value > max) {
                max = value;
            }
        }

        ArrayList<Polyline> temp = new ArrayList<>();
        for (int i = 0; i < locs.size() - 1; i++) {
            Polyline line = new Polyline();
            line.setPoints(points.subList(i, i + 2));
            line.setColor(getColor(accels[i], max));
            line.setWidth(5f);
            temp.add(line);
        }
        lines.postValue(temp);
    }

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
        double dLat = Math.toRadians(loc2.getLatitude() - loc1.getLatitude());
        double dLon = Math.toRadians(loc2.getLongitude() - loc1.getLongitude());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.sin(dLon / 2) * Math.sin(dLon / 2)
                * Math.cos(Math.toRadians(loc1.getLatitude())) * Math.cos(Math.toRadians(loc2.getLatitude()));
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
