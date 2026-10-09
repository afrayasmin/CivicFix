package com.example.civicfix;

import android.app.Application;
import android.content.IntentFilter;
import android.net.ConnectivityManager;

import androidx.core.content.ContextCompat;

import com.example.civicfix.network.ConnectivityReceiver;
import com.example.civicfix.sync.SyncScheduler;
import com.example.civicfix.util.NetworkUtil;

public class CivicFixApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        ConnectivityReceiver.online.setValue(NetworkUtil.isOnline(this));

        // Receivers for connectivity changes must be registered in code (Android 7+)
        ContextCompat.registerReceiver(
                this,
                new ConnectivityReceiver(),
                new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED);

        // Sync anything left over from earlier sessions
        SyncScheduler.enqueue(this);
    }
}