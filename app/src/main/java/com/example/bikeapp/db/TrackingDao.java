package com.example.bikeapp.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

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

    @Query("SELECT * FROM LocationData WHERE timestamp > :minTimestamp")
    List<LocationData> getLocation(long minTimestamp);

    @Query("SELECT * FROM AccelerometerData WHERE timestamp > :minTimestamp")
    List<AccelerometerData> getAccel(long minTimestamp);

    @Delete
    void deleteLocation(LocationData loc);

    @Delete
    void deleteAccel(AccelerometerData acc);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAccelBatch(List<AccelerometerData> accelList);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertLocBatch(List<LocationData> locList);

    @Query("SELECT MAX(timestamp) FROM AccelerometerData")
    long getMaxAccelTime();

    @Query("SELECT MAX(timestamp) FROM LocationData")
    long getMaxLocTime();

    @Query("SELECT MIN(timestamp) FROM AccelerometerData WHERE tripID=:tripID")
    long getTripStart(int tripID);

    @Query("SELECT MAX(timestamp) FROM AccelerometerData WHERE tripID=:tripID")
    long getTripEnd(int tripID);

    @Query("SELECT * FROM LocationData WHERE tripID=:tripID ORDER BY timestamp ASC")
    List<LocationData> getTripLocs(int tripID);

    @Query("SELECT AVG(z * z) FROM AccelerometerData WHERE tripID=:tripID")
    double getMSAccel(int tripID);

    @Query("SELECT MAX(latitude) FROM LocationData WHERE tripID=:tripID")
    double getMaxLat(int tripID);

    @Query("SELECT MIN(latitude) FROM LocationData WHERE tripID=:tripID")
    double getMinLat(int tripID);

    @Query("SELECT MAX(longitude) FROM LocationData WHERE tripID=:tripID")
    double getMaxLon(int tripID);

    @Query("SELECT MIN(longitude) FROM LocationData WHERE tripID=:tripID")
    double getMinLon(int tripID);

    @Query("SELECT AVG(z * z) FROM AccelerometerData WHERE timestamp >= :start AND timestamp <= :end")
    double getRMSTime(long start, long end);

    @Query("SELECT MIN(tripID) FROM AccelerometerData")
    int getMinTrip();
}
