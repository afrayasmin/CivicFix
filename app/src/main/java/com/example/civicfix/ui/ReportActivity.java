package com.example.civicfix.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.civicfix.databinding.ActivityReportBinding;
import com.example.civicfix.db.AppDatabase;
import com.example.civicfix.model.Report;

public class ReportActivity extends AppCompatActivity {

    private ActivityReportBinding binding;

    private final String[] categories = {
            Report.CAT_ROADS,
            Report.CAT_POWER,
            Report.CAT_WATER,
            Report.CAT_SANITATION
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.spCategory.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, categories));

        binding.btnPhoto.setOnClickListener(v ->
                Toast.makeText(this, "Camera comes in Step 4", Toast.LENGTH_SHORT).show());

        binding.btnSubmit.setOnClickListener(v -> submitReport());
    }

    private String selectedSeverity() {
        int id = binding.rgSeverity.getCheckedRadioButtonId();
        if (id == binding.rbLow.getId()) return Report.SEV_LOW;
        if (id == binding.rbCritical.getId()) return Report.SEV_CRITICAL;
        return Report.SEV_MEDIUM;
    }

    private void submitReport() {
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
        r.syncState = Report.SYNC_PENDING;
        r.createdAt = System.currentTimeMillis();
        // latitude, longitude, address and photo are added in Steps 4 and 5

        binding.btnSubmit.setEnabled(false);
        AppDatabase.dbExecutor.execute(() -> {
            AppDatabase.getInstance(this).reportDao().insert(r);
            runOnUiThread(() -> {
                Toast.makeText(this, "Report saved. It will upload when you're online.",
                        Toast.LENGTH_LONG).show();
                finish();
            });
        });
    }
}