package com.example.civicfix.ui;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.example.civicfix.databinding.ActivityIssueDetailBinding;
import com.example.civicfix.db.AppDatabase;
import com.example.civicfix.model.FeedItem;
import com.example.civicfix.sync.CloudUploader;
import com.example.civicfix.util.GrievanceMailer;
import com.example.civicfix.util.NetworkUtil;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class IssueDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "report_id";
    private static final String TAG = "CivicFix";

    private ActivityIssueDetailBinding b;
    private ListenerRegistration registration;
    private FeedItem current;

    private String officerDept = null;   // set when the signed-in user is an officer
    private File proofFile;
    private boolean busy = false;
    private String afterPhotoUrl;    // set when the report has a proof-of-fix photo

    // ---------------- Proof-of-fix: camera and gallery ----------------

    private final ActivityResultLauncher<Uri> takeProofLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && proofFile != null) {
                    uploadProofAndResolve(proofFile);
                }
            });

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    launchProofCamera();
                } else {
                    Toast.makeText(this, "Camera permission is needed to take the proof photo. "
                                    + "You can choose a photo from the gallery instead.",
                            Toast.LENGTH_LONG).show();
                }
            });

    // Picks an existing image from the device's shared media
    private final ActivityResultLauncher<String> proofGalleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) importProofFromGallery(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityIssueDetailBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        setTitle("Report details");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        b.btnNavigate.setOnClickListener(v -> navigate());
        b.btnEmail.setOnClickListener(v -> {
            if (current != null) GrievanceMailer.send(this, current);
        });
        b.btnInspect.setOnClickListener(v -> updateStatus("Under Inspection"));
        b.btnResolve.setOnClickListener(v -> startResolve());
        b.ivPhoto.setOnClickListener(v ->
                openPhoto(current != null ? current.photoUrl : null, "Reported hazard"));
        b.ivBefore.setOnClickListener(v ->
                openPhoto(current != null ? current.photoUrl : null, "Before the repair"));
        b.ivAfter.setOnClickListener(v -> openPhoto(afterPhotoUrl, "After the repair"));

    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        String id = getIntent().getStringExtra(EXTRA_ID);
        if (id == null) {
            finish();
            return;
        }

        loadOfficerRole();

        // Live updates: the status tracker changes as soon as an officer updates it
        registration = reportRef(id).addSnapshotListener((doc, error) -> {
            if (error != null) {
                Log.e(TAG, "Detail listener failed", error);
                return;
            }
            if (doc == null || !doc.exists()) {
                Toast.makeText(this, "This report is no longer available",
                        Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            bind(doc);
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    private DocumentReference reportRef(String id) {
        return FirebaseFirestore.getInstance().collection("reports").document(id);
    }

    // ---------------- Officer role ----------------

    private void loadOfficerRole() {
        officerDept = null;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) return;

        FirebaseFirestore.getInstance().collection("officers").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    officerDept = doc.exists() ? doc.getString("department") : null;
                    refreshOfficerUi();
                });
    }

    private void refreshOfficerUi() {
        boolean show = current != null
                && officerDept != null
                && officerDept.equals(current.category)
                && !"Resolved".equals(current.status);

        b.officerSection.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;

        b.btnInspect.setVisibility("Submitted".equals(current.status) ? View.VISIBLE : View.GONE);
        b.btnInspect.setEnabled(!busy);
        b.btnResolve.setEnabled(!busy);
        b.tvOfficerBusy.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void setBusy(boolean value) {
        busy = value;
        refreshOfficerUi();
    }

    // ---------------- Screen content ----------------

    private void bind(DocumentSnapshot doc) {
        FeedItem f = FeedItem.from(doc);
        current = f;

        Glide.with(this).load(f.photoUrl).into(b.ivPhoto);

        b.tvUrgent.setVisibility(f.isHighUrgency() ? View.VISIBLE : View.GONE);
        b.tvDepartment.setText(f.category);
        b.tvSeverity.setText("Urgency: " + (f.severity != null ? f.severity : "Medium"));
        b.tvDescription.setText(f.description);

        String where = (f.address != null && !f.address.isEmpty())
                ? f.address
                : String.format(Locale.US, "%.5f, %.5f", f.latitude, f.longitude);
        b.tvAddress.setText("📍 " + where);

        if (f.landmark != null && !f.landmark.isEmpty()) {
            b.tvLandmark.setText("Near: " + f.landmark);
            b.tvLandmark.setVisibility(View.VISIBLE);
        } else {
            b.tvLandmark.setVisibility(View.GONE);
        }

        b.tvMeta.setText("Reported " + DateUtils.getRelativeTimeSpanString(
                f.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                + " (" + formatTime(f.createdAt) + ")  ·  👍 " + f.upvotes + " noticed this");

        // Status tracker (custom view)
        int step = 0;
        if ("Under Inspection".equals(f.status)) step = 1;
        else if ("Resolved".equals(f.status)) step = 2;
        b.statusTracker.setStep(step);

        // Before and after photos
        String after = doc.getString("afterPhotoUrl");
        afterPhotoUrl = after;
        if (after != null && !after.isEmpty()) {
            b.proofSection.setVisibility(View.VISIBLE);
            Glide.with(this).load(f.photoUrl).into(b.ivBefore);
            Glide.with(this).load(after).into(b.ivAfter);
        } else {
            b.proofSection.setVisibility(View.GONE);
        }

        // Audit trail written by officers
        StringBuilder log = new StringBuilder();
        Object history = doc.get("statusHistory");
        if (history instanceof List) {
            for (Object entry : (List<?>) history) {
                if (!(entry instanceof Map)) continue;
                Map<?, ?> m = (Map<?, ?>) entry;
                Object at = m.get("at");
                long time = at instanceof Number ? ((Number) at).longValue() : 0;
                log.append("• ").append(m.get("status"))
                        .append(" · ").append(formatTime(time))
                        .append(" · ").append(m.get("by")).append("\n");
            }
        }
        if (log.length() > 0) {
            b.tvHistory.setText("Activity log\n" + log.toString().trim());
            b.tvHistory.setVisibility(View.VISIBLE);
        } else {
            b.tvHistory.setVisibility(View.GONE);
        }

        refreshOfficerUi();
    }

    private String formatTime(long millis) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(millis));
    }

    // ---------------- Officer actions ----------------

    private Map<String, Object> historyEntry(String status) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        Map<String, Object> entry = new HashMap<>();
        entry.put("status", status);
        entry.put("at", System.currentTimeMillis());
        entry.put("by", user != null && user.getEmail() != null ? user.getEmail() : "officer");
        return entry;
    }

    private void updateStatus(String newStatus) {
        if (current == null) return;
        if (!NetworkUtil.isOnline(this)) {
            Toast.makeText(this, "Connect to the internet to update a report.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> update = new HashMap<>();
        update.put("status", newStatus);
        update.put("statusHistory", FieldValue.arrayUnion(historyEntry(newStatus)));

        setBusy(true);
        reportRef(current.id).update(update)
                .addOnSuccessListener(v -> {
                    setBusy(false);
                    Toast.makeText(this, "Status updated", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    setBusy(false);
                    Log.e(TAG, "Status update failed", e);
                    Toast.makeText(this, "Update failed: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
    }

    /** Asks the officer how to provide the proof photo: camera or gallery. */
    private void startResolve() {
        if (current == null) return;
        if (!NetworkUtil.isOnline(this)) {
            Toast.makeText(this, "Connect to the internet to resolve a report.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Proof of the repair")
                .setItems(new String[]{"Take a photo", "Choose from gallery"},
                        (dialog, which) -> {
                            if (which == 0) {
                                openProofCamera();
                            } else {
                                proofGalleryLauncher.launch("image/*");
                            }
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private File photosDir() {
        File dir = new File(getFilesDir(), "photos");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    // --- Camera ---

    private void openProofCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchProofCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchProofCamera() {
        proofFile = new File(photosDir(), "PROOF_" + System.currentTimeMillis() + ".jpg");
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", proofFile);
        takeProofLauncher.launch(uri);
    }

    // --- Gallery (ContentResolver) ---

    private void importProofFromGallery(Uri uri) {
        final File target = new File(photosDir(), "PROOF_" + System.currentTimeMillis() + ".jpg");

        setBusy(true);
        AppDatabase.dbExecutor.execute(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(target)) {
                if (in == null) throw new IOException("Cannot open the selected image");
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            } catch (Exception e) {
                Log.e(TAG, "Could not read the proof photo", e);
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(this, "Could not read the selected image",
                            Toast.LENGTH_LONG).show();
                });
                return;
            }
            runOnUiThread(() -> uploadProofAndResolve(target));
        });
    }

    // --- Upload and resolve (same for camera and gallery) ---

    private void uploadProofAndResolve(File photo) {
        if (current == null) return;
        final String reportId = current.id;

        setBusy(true);
        AppDatabase.dbExecutor.execute(() -> {
            try {
                String url = CloudUploader.uploadPhoto(photo.getAbsolutePath());

                Map<String, Object> update = new HashMap<>();
                update.put("status", "Resolved");
                update.put("afterPhotoUrl", url);
                update.put("resolvedAt", System.currentTimeMillis());
                update.put("statusHistory", FieldValue.arrayUnion(historyEntry("Resolved")));

                Tasks.await(reportRef(reportId).update(update), 30, TimeUnit.SECONDS);

                // Live community counter (Realtime Database). A failure here must not fail the update.
                try {
                    Tasks.await(FirebaseDatabase.getInstance().getReference("stats/resolved")
                            .setValue(ServerValue.increment(1)), 10, TimeUnit.SECONDS);
                } catch (Exception statsError) {
                    Log.w(TAG, "Stats update failed", statsError);
                }

                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(this, "Marked as resolved with proof photo",
                            Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Resolve failed", e);
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(this, "Could not upload the proof photo: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void openPhoto(String url, String title) {
        if (url == null || url.isEmpty()) return;
        Intent intent = new Intent(this, PhotoViewerActivity.class);
        intent.putExtra(PhotoViewerActivity.EXTRA_URL, url);
        intent.putExtra(PhotoViewerActivity.EXTRA_TITLE, title);
        startActivity(intent);
    }

    // ---------------- Navigation (implicit intents) ----------------

    private void navigate() {
        if (current == null) return;
        String latLng = current.latitude + "," + current.longitude;

        // First choice: turn-by-turn navigation in Google Maps
        Intent nav = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + latLng));
        nav.setPackage("com.google.android.apps.maps");
        try {
            startActivity(nav);
            return;
        } catch (ActivityNotFoundException e) {
            Log.d(TAG, "Google Maps app not available, opening in browser");
        }

        // Fallback: any app or browser that can open a map link
        Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://www.google.com/maps/search/?api=1&query=" + latLng));
        try {
            startActivity(web);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app available to open the map", Toast.LENGTH_SHORT).show();
        }
    }
}