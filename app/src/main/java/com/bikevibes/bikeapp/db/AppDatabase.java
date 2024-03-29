package com.bikevibes.bikeapp.db;

import android.content.Context;

import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The database class for the Room database.
 * Builds the database and offers access to its thread pool and Data Access Object.
 * The database instance follows a singleton pattern.
 */
@Database(
        entities = {AccelerometerData.class, LocationData.class, Segment.class, TripSurface.class},
        version = 3,
        autoMigrations = {
                @AutoMigration(from = 1, to = 2),
                @AutoMigration(from = 2, to = 3)
        }
)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    public abstract TrackingDao myDao();

    private static volatile AppDatabase instance;
    private static final String DB_NAME = "bike.db";
    private static final int NUMBER_OF_THREADS = 1;
    private static final ExecutorService databaseExecutor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    /**
     * Get the database instance or construct it if needed.
     * @param context - the context used to create the database
     * @return - the singleton database instance
     */
    public static AppDatabase getDatabase(final Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, DB_NAME).build();
                }
            }
        }
        return instance;
    }

    public static ExecutorService getExecutor() {
        return databaseExecutor;
    }
}
