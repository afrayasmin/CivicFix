package com.example.civicfix.network;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.provider.Settings;

import androidx.lifecycle.MutableLiveData;

import com.example.civicfix.sync.SyncScheduler;
import com.example.civicfix.util.NetworkUtil;

/**
 * Listens to system broadcasts: connectivity changes, airplane mode and battery level.
 * Screens observe the LiveData values below to show banners.
 */
public class ConnectivityReceiver extends BroadcastReceiver {

    public static final MutableLiveData<Boolean> online = new MutableLiveData<>(true);
    public static final MutableLiveData<Boolean> airplaneMode = new MutableLiveData<>(false);
    public static final MutableLiveData<Boolean> batteryLow = new MutableLiveData<>(false);

    public static IntentFilter intentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        return filter;
    }

    public static boolean isAirplaneModeOn(Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        if (Intent.ACTION_BATTERY_LOW.equals(action)) {
            batteryLow.postValue(true);
            return;
        }
        if (Intent.ACTION_BATTERY_OKAY.equals(action)) {
            batteryLow.postValue(false);
            return;
        }
        if (Intent.ACTION_AIRPLANE_MODE_CHANGED.equals(action)) {
            airplaneMode.postValue(isAirplaneModeOn(context));
        }

        // Connectivity or airplane mode changed: refresh the online state
        boolean isOnline = NetworkUtil.isOnline(context);
        online.postValue(isOnline);
        if (isOnline) {
            SyncScheduler.enqueue(context); // connection is back: sync now
        }
    }
}