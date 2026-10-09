package com.example.civicfix.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.example.civicfix.databinding.ActivityReportBinding;
import com.example.civicfix.db.AppDatabase;
import com.example.civicfix.model.Report;
import com.example.civicfix.network.GeocodingClient;
import com.example.civicfix.sync.SyncScheduler;
import com.example.civicfix.util.NetworkUtil;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.io.File;
import java.util.Locale;

public class ReportActivity extends AppCompatActivity {

    private ActivityReportBinding binding;
    private FusedLocationProviderClient locationClient;

    private File photoFile;     // the confirmed photo
    private File pendingFile;   // the photo currently being taken

    private double latitude;
    private double longitude;
    private boolean hasLocation = false;
    private String address;     // from reverse geocoding; null if offline

    private final String[] categories = {
            Report.CAT_ROADS,
            Report.CAT_POWER,
            Report.CAT_WATER,
            Report.CAT_SANITATION
    };

    // --- Activity result launchers ---

    private final ActivityResultLauncher<Uri> takePictureLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && pendingFile != null) {
                    photoFile = pendingFile;
                    Glide.with(this).load(photoFile).into(binding.ivPhoto);
                    binding.btnPhoto.setText("Retake photo");
                    fetchLocation(); // refresh GPS at the moment of capture
                }
            });

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    launchCamera();
                } else {
                    Toast.makeText(this, "Camera permission is needed to take a photo",
                            Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        if (hasLocationPermission()) {
                            fetchLocation();
                        } else {
                            binding.tvLocation.setText("Location: permission denied");
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        locationClient = LocationServices.getFusedLocationProviderClient(this);

        binding.spCategory.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, categories));

        binding.btnPhoto.setOnClickListener(v -> onTakePhotoClicked());
        binding.btnSubmit.setOnClickListener(v -> submitReport());

        fetchLocation();
    }

    // ---------------- Camera ----------------

    private void onTakePhotoClicked() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void launchCamera() {
        File dir = new File(getFilesDir(), "photos");
        if (!dir.exists()) dir.mkdirs();
        pendingFile = new File(dir, "IMG_" + System.currentTimeMillis() + ".jpg");

        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", pendingFile);
        takePictureLauncher.launch(uri);
    }

    // ---------------- GPS ----------------

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private void fetchLocation() {
        if (!hasLocationPermission()) {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
            return;
        }

        binding.tvLocation.setText("Location: getting GPS fix...");

        locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,
                        new CancellationTokenSource().getToken())
                .addOnSuccessListener(this, loc -> {
                    if (loc != null) {
                        latitude = loc.getLatitude();
                        longitude = loc.getLongitude();
                        hasLocation = true;
                        showLocation(null);
                        lookupAddress(latitude, longitude);
                    } else {
                        binding.tvLocation.setText("Location: no GPS fix. Turn on Location and go outside.");
                    }
                })
                .addOnFailureListener(this, e ->
                        binding.tvLocation.setText("Location: failed to get GPS fix"));
    }

    // ---------------- Reverse geocoding ----------------

    private void lookupAddress(double lat, double lon) {
        address = null;
        AppDatabase.dbExecutor.execute(() -> {
            String result = GeocodingClient.reverse(lat, lon);
            runOnUiThread(() -> {
                address = result;
                showLocation(result);
            });
        });
    }

    private void showLocation(String addr) {
        String coords = String.format(Locale.US, "%.5f, %.5f", latitude, longitude);
        if (addr != null) {
            binding.tvLocation.setText("Location: " + addr + "\n(" + coords + ")");
        } else {
            binding.tvLocation.setText("Location: " + coords
                    + "\n(street address will be added when online)");
        }
    }

    // ---------------- Submit ----------------

    private String selectedSeverity() {
        int id = binding.rgSeverity.getCheckedRadioButtonId();
        if (id == binding.rbLow.getId()) return Report.SEV_LOW;
        if (id == binding.rbCritical.getId()) return Report.SEV_CRITICAL;
        return Report.SEV_MEDIUM;
    }

    private void submitReport() {
        if (photoFile == null) {
            Toast.makeText(this, "Please take a photo of the hazard", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasLocation) {
            Toast.makeText(this, "Waiting for GPS location. Try again in a moment.",
                    Toast.LENGTH_SHORT).show();
            fetchLocation();
            return;
        }
        String description = binding.etDescription.getText().toString().trim();
        if (description.isEmpty()) {
            binding.etDescription.setError("Please describe the hazard");
            return;
        }

        Report r = new Report();
        r.category = categories[binding.spCategory.getSelectedItemPosition()];
        r.severity = selectedSeverity();
        r.description = description;
        r.landmark = binding.etLandmark.getText().toString().trim();
        r.latitude = latitude;
        r.longitude = longitude;
        r.address = address; // may be null if offline; the sync worker fills it later
        r.localPhotoPath = photoFile.getAbsolutePath();
        r.syncState = Report.SYNC_PENDING;
        r.createdAt = System.currentTimeMillis();

        binding.btnSubmit.setEnabled(false);
        AppDatabase.dbExecutor.execute(() -> {
            AppDatabase.getInstance(this).reportDao().insert(r);
            SyncScheduler.enqueue(getApplicationContext()); // uploads now if online, later if not
            boolean online = NetworkUtil.isOnline(this);
            runOnUiThread(() -> {
                Toast.makeText(this,
                        online ? "Report saved. Uploading now..."
                                : "You're offline. Report saved and will upload automatically when you're back online.",
                        Toast.LENGTH_LONG).show();
                finish();
            });
        });
    }
}