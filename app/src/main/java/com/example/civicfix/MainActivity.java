package com.example.civicfix;

import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.example.civicfix.db.AppDatabase;
import com.example.civicfix.model.Report;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AppDatabase db = AppDatabase.getInstance(this);

        // Observe all reports; this runs whenever the table changes
        db.reportDao().getAllLive().observe(this, reports ->
                Log.d("CivicFix", "Reports in DB: " + reports.size()));

        // Insert one test report on a background thread
        AppDatabase.dbExecutor.execute(() -> {
            Report r = new Report();
            r.category = Report.CAT_ROADS;
            r.severity = Report.SEV_CRITICAL;
            r.description = "Test pothole";
            r.createdAt = System.currentTimeMillis();
            db.reportDao().insert(r);
        });
    }
}