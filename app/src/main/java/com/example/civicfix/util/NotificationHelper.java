package com.example.civicfix.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.civicfix.MainActivity;

public class NotificationHelper {

    public static final int PROGRESS_ID = 1001;
    public static final int DONE_ID = 1002;

    private static final String CHANNEL_PROGRESS = "civicfix_progress";
    private static final String CHANNEL_DONE = "civicfix_done";

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_PROGRESS, "Report uploads", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_DONE, "Upload finished", NotificationManager.IMPORTANCE_DEFAULT));
    }

    /** Ongoing notification shown while the foreground sync service is running. */
    public static Notification progress(Context context, String text, int done, int total) {
        return new NotificationCompat.Builder(context, CHANNEL_PROGRESS)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("CivicFix")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(total, done, total == 0)
                .setContentIntent(openApp(context))
                .build();
    }

    @SuppressLint("MissingPermission")
    public static void showUploaded(Context context, int count) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Notification n = new NotificationCompat.Builder(context, CHANNEL_DONE)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("CivicFix")
                .setContentText("✅ " + count + (count == 1 ? " report" : " reports")
                        + " uploaded. Thank you for helping your neighbourhood!")
                .setAutoCancel(true)
                .setContentIntent(openApp(context))
                .build();
        NotificationManagerCompat.from(context).notify(DONE_ID, n);
    }

    private static PendingIntent openApp(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}