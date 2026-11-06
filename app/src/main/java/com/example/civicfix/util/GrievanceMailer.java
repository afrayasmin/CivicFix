package com.example.civicfix.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.example.civicfix.db.AppDatabase;
import com.example.civicfix.model.FeedItem;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class GrievanceMailer {

    private static final String TAG = "CivicFix";

    public static void send(Activity activity, FeedItem f) {
        String mapsLink = String.format(Locale.US,
                "https://www.google.com/maps/search/?api=1&query=%f,%f", f.latitude, f.longitude);
        String where = (f.address != null && !f.address.isEmpty()) ? f.address : "Not available";
        String when = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(f.createdAt));

        String subject = "Civic grievance: " + f.category
                + (f.address != null && !f.address.isEmpty() ? " - " + f.address : "");

        StringBuilder body = new StringBuilder();
        body.append("Dear Sir/Madam,\n\n");
        body.append("I would like to report a civic issue through the CivicFix app.\n\n");
        body.append("Department: ").append(f.category).append("\n");
        body.append("Urgency: ").append(f.severity != null ? f.severity : "Medium").append("\n");
        body.append("Description: ").append(f.description).append("\n");
        body.append("Location: ").append(where).append("\n");
        if (f.landmark != null && !f.landmark.isEmpty()) {
            body.append("Landmark: ").append(f.landmark).append("\n");
        }
        body.append(String.format(Locale.US, "GPS coordinates: %f, %f\n", f.latitude, f.longitude));
        body.append("Map: ").append(mapsLink).append("\n");
        body.append("Reported on: ").append(when).append("\n");
        body.append("Citizens who noticed this: ").append(f.upvotes).append("\n");
        body.append("Current status: ").append(f.status).append("\n");
        if (f.photoUrl != null && !f.photoUrl.isEmpty()) {
            body.append("Photo: ").append(f.photoUrl).append("\n");
        }
        body.append("\nPlease inspect and resolve this at the earliest.\n\n");
        body.append("Regards,\nA concerned citizen");

        Toast.makeText(activity, "Preparing your email...", Toast.LENGTH_SHORT).show();

        Context appContext = activity.getApplicationContext();
        AppDatabase.dbExecutor.execute(() -> {
            Uri attachment = null;
            if (f.photoUrl != null && !f.photoUrl.isEmpty()) {
                try {
                    File cached = Glide.with(appContext).asFile().load(f.photoUrl)
                            .submit().get(20, TimeUnit.SECONDS);

                    File dir = new File(appContext.getCacheDir(), "shared");
                    if (!dir.exists()) dir.mkdirs();
                    File copy = new File(dir, "report_" + f.id + ".jpg");
                    copyFile(cached, copy);

                    attachment = FileProvider.getUriForFile(
                            appContext, appContext.getPackageName() + ".fileprovider", copy);
                } catch (Exception e) {
                    Log.w(TAG, "Could not attach photo, sending the link only", e);
                }
            }

            final Uri photo = attachment;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing()) return;
                launchEmail(activity, subject, body.toString(), photo);
            });
        });
    }

    private static void launchEmail(Activity activity, String subject, String body, Uri photo) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("message/rfc822");
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{CivicConfig.COUNCIL_EMAIL});
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, body);

        if (photo != null) {
            intent.putExtra(Intent.EXTRA_STREAM, photo);
            intent.setClipData(ClipData.newRawUri("Report photo", photo));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }

        try {
            activity.startActivity(Intent.createChooser(intent, "Send grievance email"));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, "No email app found on this phone",
                    Toast.LENGTH_LONG).show();
        }
    }

    private static void copyFile(File from, File to) throws Exception {
        try (InputStream in = new FileInputStream(from);
             OutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        }
    }
}