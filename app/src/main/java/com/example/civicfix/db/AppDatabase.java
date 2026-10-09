package com.example.civicfix.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.civicfix.model.Report;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {Report.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract ReportDao reportDao();

    private static volatile AppDatabase INSTANCE;

    // Room does not allow database work on the main thread, so we use this
    public static final ExecutorService dbExecutor = Executors.newFixedThreadPool(2);

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "civicfix.db").build();
                }
            }
        }
        return INSTANCE;
    }
}