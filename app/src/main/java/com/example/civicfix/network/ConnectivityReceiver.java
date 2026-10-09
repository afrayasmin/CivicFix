package com.example.civicfix.network;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.lifecycle.MutableLiveData;

import com.example.civicfix.sync.SyncScheduler;
import com.example.civicfix.util.NetworkUtil;

public class ConnectivityReceiver extends BroadcastReceiver {

    // Screens observe this to show or hide the offline banner
    public static final MutableLiveData<Boolean> online = new MutableLiveData<>(true);

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean isOnline = NetworkUtil.isOnline(context);
        online.postValue(isOnline);
        if (isOnline) {
            SyncScheduler.enqueue(context); // connection is back: sync now
        }
    }
}