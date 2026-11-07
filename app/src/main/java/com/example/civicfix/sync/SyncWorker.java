package com.example.civicfix.sync;

import android.app.Notification;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.civicfix.db.AppDatabase;
import com.example.civicfix.db.ReportDao;
import com.example.civicfix.model.Report;
import com.example.civicfix.network.GeocodingClient;
import com.example.civicfix.util.NotificationHelper;

import java.util.List;

public class SyncWorker extends Worker {

    private static final String TAG = "CivicFix";

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        ReportDao dao = AppDatabase.getInstance(context).reportDao();
        List<Report> pending = dao.getPendingReports();
        Log.d(TAG, "SyncWorker started, pending reports: " + pending.size());

        if (pending.isEmpty()) return Result.success();

        int uploaded = 0;
        boolean anyFailed = false;

        for (int i = 0; i < pending.size(); i++) {
            Report r = pending.get(i);
            showProgress(i, pending.size());

            try {
                // Fill in the street address if it could not be fetched offline
                if (r.address == null) {
                    String addr = GeocodingClient.reverse(r.latitude, r.longitude);
                    if (addr != null) {
                        dao.updateAddress(r.id, addr);
                        r.address = addr;
                    }
                }

                String[] result = CloudUploader.upload(context, r);
                dao.markSynced(r.id, result[0], result[1]);
                uploaded++;
                Log.d(TAG, "Synced report " + r.id);

            } catch (Exception e) {
                Log.e(TAG, "Sync failed for report " + r.id, e);
                anyFailed = true;
            }
        }

        if (uploaded > 0) {
            NotificationHelper.showUploaded(context, uploaded);
        }

        // retry() tells WorkManager to try again later with backoff
        return anyFailed ? Result.retry() : Result.success();
    }

    /** Promotes this worker to a foreground service and updates the notification. */
    private void showProgress(int done, int total) {
        try {
            String text = "Uploading report " + (done + 1) + " of " + total + "...";
            Notification notification = NotificationHelper.progress(
                    getApplicationContext(), text, done, total);

            ForegroundInfo info = Build.VERSION.SDK_INT >= 29
                    ? new ForegroundInfo(NotificationHelper.PROGRESS_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                    : new ForegroundInfo(NotificationHelper.PROGRESS_ID, notification);

            setForegroundAsync(info);
        } catch (Exception e) {
            // Android can refuse foreground services started from the background.
            // The upload itself still works without the notification.
            Log.w(TAG, "Could not show the foreground notification", e);
        }
    }
}