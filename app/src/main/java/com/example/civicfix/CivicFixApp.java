package com.example.civicfix;

import android.app.Application;

import androidx.core.content.ContextCompat;

import com.example.civicfix.network.ConnectivityReceiver;
import com.example.civicfix.sync.SyncScheduler;
import com.example.civicfix.util.NetworkUtil;
import com.example.civicfix.util.NotificationHelper;

public class CivicFixApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationHelper.createChannels(this);

        ConnectivityReceiver.online.setValue(NetworkUtil.isOnline(this));
        ConnectivityReceiver.airplaneMode.setValue(ConnectivityReceiver.isAirplaneModeOn(this));

        // These system broadcasts must be registered in code on modern Android
        ContextCompat.registerReceiver(
                this,
                new ConnectivityReceiver(),
                ConnectivityReceiver.intentFilter(),
                ContextCompat.RECEIVER_NOT_EXPORTED);

        // Sync anything left over from earlier sessions
        SyncScheduler.enqueue(this);
    }
}