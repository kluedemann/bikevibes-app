package com.bikevibes.bikeapp.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.Date;
import java.util.List;

/**
 * The Data Access Object for the Room database.
 * Provides methods for interacting with the database.
 * Uses annotations to automatically generate the code for each method.
 */
@Dao
public interface TrackingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertLocation(LocationData loc);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAccel(AccelerometerData acc);

    @Query("SELECT * FROM LocationData WHERE tripID <= :maxTrip")
    List<LocationData> getLocList(int maxTrip);

    @Query("SELECT * FROM accelerometerdata WHERE tripID <= :maxTrip")
    List<AccelerometerData> getAccList(int maxTrip);

    @Delete
    void deleteLocation(LocationData loc);

    @Delete
    void deleteAccel(AccelerometerData acc);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAccelBatch(List<AccelerometerData> accelList);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertLocBatch(List<LocationData> locList);

    @Query("SELECT * FROM LocationData WHERE tripID=:tripID ORDER BY timestamp ASC")
    List<LocationData> getTripLocs(int tripID);

    @Query("SELECT MAX(lat2) FROM segment WHERE tripID=:tripID")
    double getMaxLat(int tripID);

    @Query("SELECT MIN(lat2) FROM segment WHERE tripID=:tripID")
    double getMinLat(int tripID);

    @Query("SELECT MAX(lon2) FROM segment WHERE tripID=:tripID")
    double getMaxLon(int tripID);

    @Query("SELECT MIN(lon2) FROM segment WHERE tripID=:tripID")
    double getMinLon(int tripID);

    @Query("SELECT AVG(z * z) FROM AccelerometerData WHERE timestamp >= :start AND timestamp <= :end")
    double getRmsZAccel(Date start, Date end);

    @Query("DELETE FROM AccelerometerData")
    void deleteAllAccel();

    @Query("DELETE FROM LocationData")
    void deleteAllLoc();

    @Query("SELECT MAX(ABS(z)) FROM AccelerometerData WHERE timestamp >= :start AND timestamp <= :end")
    double getMaxZAccel(Date start, Date end);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertSegments(List<Segment> segments);

    @Query("SELECT MIN(ts1) FROM segment WHERE tripID = :tripID")
    Date getTripStartSeg(int tripID);

    @Query("SELECT MAX(ts2) FROM segment WHERE tripID = :tripID")
    Date getTripEndSeg(int tripID);

    @Query("SELECT AVG(rmsZAccel) FROM segment WHERE tripID = :tripID")
    double getAvgAccel(int tripID);

    @Query("SELECT * FROM segment WHERE tripID = :tripID")
    List<Segment> getSegments(int tripID);

    @Query("DELETE FROM accelerometerdata WHERE tripID = :tripID AND timestamp < :timestamp")
    void delAccLt(int tripID, Date timestamp);

    @Query("DELETE FROM locationdata WHERE tripID = :tripID AND timestamp < :timestamp")
    void delLocLt(int tripID, Date timestamp);

    @Query("DELETE FROM accelerometerdata WHERE tripID = :tripID AND timestamp > :timestamp")
    void delAccGt(int tripID, Date timestamp);

    @Query("DELETE FROM locationdata WHERE tripID = :tripID AND timestamp > :timestamp")
    void delLocGt(int tripID, Date timestamp);

    @Query("SELECT DISTINCT(tripID) FROM segment ORDER BY tripID ASC")
    LiveData<List<Integer>> getTrips();

    @Query("DELETE FROM segment")
    void deleteAllSegments();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertSurface(TripSurface trip);

    @Delete
    void deleteSurface(TripSurface trip);

    @Query("SELECT * FROM tripsurface WHERE tripID <= :maxTrip")
    List<TripSurface> getSurfaceList(int maxTrip);

    @Query("DELETE FROM TripSurface")
    void deleteAllSurfaces();

    @Query("DELETE FROM tripsurface WHERE tripID = :tripID")
    void deleteTripSurface(int tripID);

    @Query("SELECT COUNT(*) FROM locationdata WHERE tripID = :tripID")
    int countLocs(int tripID);

    @Update
    void updateSurface(TripSurface trip);
}
