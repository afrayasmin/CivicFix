package com.example.civicfix.sync;

import android.content.Context;

import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class SyncScheduler {

    public static void enqueue(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED) // only runs when online
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();

        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork("civicfix_sync",
                        ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    }
}