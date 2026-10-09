package com.example.civicfix.sync;

import android.content.Context;

import com.example.civicfix.model.Report;

public class CloudUploader {

    /**
     * Step 6: simulated upload. Step 7 replaces this with Firebase
     * Storage (photo) + Firestore (report).
     * Returns {cloudId, photoUrl}.
     */
    public static String[] upload(Context context, Report report) throws Exception {
        Thread.sleep(1500); // pretend the network call takes time
        return new String[]{"demo-" + report.id, null};
    }
}