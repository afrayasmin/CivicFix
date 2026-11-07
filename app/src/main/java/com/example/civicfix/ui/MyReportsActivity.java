package com.example.civicfix.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.civicfix.adapter.MyReportAdapter;
import com.example.civicfix.databinding.ActivityMyReportsBinding;
import com.example.civicfix.db.AppDatabase;
import com.example.civicfix.model.Report;

import java.io.File;
import java.util.List;

public class MyReportsActivity extends AppCompatActivity {

    private ActivityMyReportsBinding b;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMyReportsBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        setTitle("My reports");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        MyReportAdapter adapter = new MyReportAdapter(new MyReportAdapter.Listener() {
            @Override
            public void onOpen(Report report) {
                openReport(report);
            }

            @Override
            public void onDelete(Report report) {
                confirmDelete(report);
            }
        });
        b.rvMine.setLayoutManager(new LinearLayoutManager(this));
        b.rvMine.setAdapter(adapter);

        // The Room database tells us whenever the list changes
        AppDatabase.getInstance(this).reportDao().getAllLive()
                .observe(this, reports -> showReports(adapter, reports));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void showReports(MyReportAdapter adapter, List<Report> reports) {
        adapter.submitList(reports);

        int waiting = 0;
        for (Report r : reports) {
            if (Report.SYNC_PENDING.equals(r.syncState)) waiting++;
        }
        b.tvSummary.setText(reports.size() + (reports.size() == 1 ? " report" : " reports")
                + " from this phone · " + waiting + " waiting to upload");
        b.tvEmpty.setVisibility(reports.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openReport(Report report) {
        if (Report.SYNC_DONE.equals(report.syncState) && report.cloudId != null) {
            Intent intent = new Intent(this, IssueDetailActivity.class);
            intent.putExtra(IssueDetailActivity.EXTRA_ID, report.cloudId);
            startActivity(intent);
        } else {
            Toast.makeText(this,
                    "This report is still waiting to upload. You can open it once it is uploaded.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDelete(Report report) {
        new AlertDialog.Builder(this)
                .setTitle("Delete this report?")
                .setMessage("It has not been uploaded yet, so it will be removed from your "
                        + "phone for good.")
                .setPositiveButton("Delete", (dialog, which) -> deleteReport(report))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteReport(Report report) {
        AppDatabase.dbExecutor.execute(() -> {
            // The query only removes reports that are still PENDING_SYNC
            int removed = AppDatabase.getInstance(this).reportDao().deletePending(report.id);
            if (removed > 0 && report.localPhotoPath != null) {
                new File(report.localPhotoPath).delete();
            }
            runOnUiThread(() -> Toast.makeText(this,
                    removed > 0 ? "Report deleted"
                            : "This report has just been uploaded, so it can no longer be deleted here.",
                    Toast.LENGTH_SHORT).show());
        });
    }
}