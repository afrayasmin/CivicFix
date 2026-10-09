package com.example.civicfix.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.civicfix.db.AppDatabase;
import com.example.civicfix.db.ReportDao;
import com.example.civicfix.model.Report;
import com.example.civicfix.network.GeocodingClient;

import java.util.List;

public class SyncWorker extends Worker {

    private static final String TAG = "CivicFix";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        ReportDao dao = AppDatabase.getInstance(getApplicationContext()).reportDao();
        List<Report> pending = dao.getPendingReports();
        Log.d(TAG, "SyncWorker started, pending reports: " + pending.size());

        boolean anyFailed = false;
        for (Report r : pending) {
            try {
                // Fill in the street address if it could not be fetched offline
                if (r.address == null) {
                    String addr = GeocodingClient.reverse(r.latitude, r.longitude);
                    if (addr != null) {
                        dao.updateAddress(r.id, addr);
                        r.address = addr;
                    }
                }

                String[] result = CloudUploader.upload(getApplicationContext(), r);
                dao.markSynced(r.id, result[0], result[1]);
                Log.d(TAG, "Synced report " + r.id);

            } catch (Exception e) {
                Log.e(TAG, "Sync failed for report " + r.id, e);
                anyFailed = true;
            }
        }

        // retry() tells WorkManager to try again later with backoff
        return anyFailed ? Result.retry() : Result.success();
    }
}