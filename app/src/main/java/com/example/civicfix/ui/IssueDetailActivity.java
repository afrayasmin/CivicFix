package com.example.civicfix.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.civicfix.databinding.ActivityIssueDetailBinding;
import com.example.civicfix.model.FeedItem;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public class IssueDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "report_id";
    private static final String TAG = "CivicFix";

    private ActivityIssueDetailBinding b;
    private ListenerRegistration registration;
    private FeedItem current;

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

        // Live updates: the status tracker changes as soon as an officer updates it
        registration = FirebaseFirestore.getInstance()
                .collection("reports").document(id)
                .addSnapshotListener((doc, error) -> {
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

        String when = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(f.createdAt));
        b.tvMeta.setText("Reported " + DateUtils.getRelativeTimeSpanString(
                f.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                + " (" + when + ")  ·  👍 " + f.upvotes + " noticed this");

        // Status tracker
        int step = 0;
        if ("Under Inspection".equals(f.status)) step = 1;
        else if ("Resolved".equals(f.status)) step = 2;
        TextView[] steps = {b.tvStep1, b.tvStep2, b.tvStep3};
        for (int i = 0; i < steps.length; i++) {
            boolean reached = i <= step;
            steps[i].setSelected(reached);
            steps[i].setTextColor(reached ? Color.WHITE : 0xFF0D47A1);
        }

        // Before and after photos, shown once an officer attaches proof of the fix
        String after = doc.getString("afterPhotoUrl");
        if (after != null && !after.isEmpty()) {
            b.proofSection.setVisibility(View.VISIBLE);
            Glide.with(this).load(f.photoUrl).into(b.ivBefore);
            Glide.with(this).load(after).into(b.ivAfter);
        } else {
            b.proofSection.setVisibility(View.GONE);
        }
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