package com.example.civicfix.sync;

import android.content.Context;
import android.util.Log;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;

import com.example.civicfix.model.Report;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class CloudUploader {

    private static final String CLOUD_NAME = "sjd8qf24";
    private static final String UPLOAD_PRESET = "civicfix_unsigned";

    /**
     * Uploads the photo to Cloudinary, then writes the report to Firestore.
     * Blocking: must be called from a background thread (the SyncWorker).
     * Returns {cloudId, photoUrl}.
     */
    public static String[] upload(Context context, Report r) throws Exception {
        // The security rules require a signed-in user (anonymous is enough)
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            Tasks.await(auth.signInAnonymously(), 20, TimeUnit.SECONDS);
        }

        // 1. Photo -> Cloudinary (optional)
        String photoUrl = null;
        if (r.localPhotoPath != null && !r.localPhotoPath.isEmpty()) {
            byte[] jpeg = compressPhoto(r.localPhotoPath);
            photoUrl = uploadToCloudinary(jpeg);
        }

        // 2. Report -> Firestore.
        // The document id is derived from the report's content, so a retry after a
        // partial failure overwrites the same document instead of creating a duplicate.
        String docId = UUID.nameUUIDFromBytes(
                (r.createdAt + "|" + r.latitude + "|" + r.longitude).getBytes()).toString();

        Map<String, Object> data = new HashMap<>();
        data.put("category", r.category);
        data.put("severity", r.severity);
        data.put("description", r.description);
        data.put("landmark", r.landmark);
        data.put("latitude", r.latitude);
        data.put("longitude", r.longitude);
        data.put("address", r.address);
        data.put("photoUrl", photoUrl);
        data.put("status", "Submitted");
        data.put("upvotes", 0);
        data.put("createdAt", r.createdAt);

        Tasks.await(FirebaseFirestore.getInstance()
                .collection("reports")
                .document(docId)
                .set(data), 30, TimeUnit.SECONDS);
        // Live community counter (Realtime Database). A failure here must not fail the sync.
        try {
            Tasks.await(FirebaseDatabase.getInstance().getReference("stats/total")
                    .setValue(ServerValue.increment(1)), 10, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.w("CivicFix", "Stats update failed", e);
        }

        return new String[]{docId, photoUrl};
    }

    // ---------- Photo compression (phone photos are several MB) ----------

    private static byte[] compressPhoto(String path) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);

        int sample = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeFile(path, opts);
        if (bmp == null) throw new IOException("Cannot decode photo: " + path);

        // Re-encoding drops the EXIF rotation tag, so apply it to the pixels first
        int orientation = new ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        int degrees = 0;
        if (orientation == ExifInterface.ORIENTATION_ROTATE_90) degrees = 90;
        else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) degrees = 180;
        else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) degrees = 270;
        if (degrees != 0) {
            Matrix m = new Matrix();
            m.postRotate(degrees);
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 75, out);
        return out.toByteArray();
    }

    /** Compresses a local photo and uploads it to Cloudinary. Returns the photo URL. */
    public static String uploadPhoto(String path) throws Exception {
        return uploadToCloudinary(compressPhoto(path));
    }

    // ---------- Cloudinary unsigned upload ----------

    private static String uploadToCloudinary(byte[] jpeg) throws Exception {
        String boundary = "----CivicFix" + System.currentTimeMillis();
        URL url = new URL("https://api.cloudinary.com/v1_1/" + CLOUD_NAME + "/image/upload");

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
                out.writeBytes("--" + boundary + "\r\n");
                out.writeBytes("Content-Disposition: form-data; name=\"upload_preset\"\r\n\r\n");
                out.writeBytes(UPLOAD_PRESET + "\r\n");

                out.writeBytes("--" + boundary + "\r\n");
                out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"report.jpg\"\r\n");
                out.writeBytes("Content-Type: image/jpeg\r\n\r\n");
                out.write(jpeg);
                out.writeBytes("\r\n--" + boundary + "--\r\n");
            }

            int code = conn.getResponseCode();
            InputStream in = (code >= 200 && code < 300)
                    ? conn.getInputStream() : conn.getErrorStream();
            String body = readAll(in);
            Log.d("CivicFix", "Cloudinary response: " + body);

            if (code < 200 || code >= 300) {
                throw new IOException("Cloudinary upload failed (" + code + "): " + body);
            }
            return new JSONObject(body).getString("secure_url");
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) != -1) buf.write(chunk, 0, n);
        return buf.toString("UTF-8");
    }
}