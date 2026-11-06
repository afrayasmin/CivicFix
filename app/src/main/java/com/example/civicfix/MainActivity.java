package com.example.civicfix;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.civicfix.adapter.ReportAdapter;
import com.example.civicfix.databinding.ActivityMainBinding;
import com.example.civicfix.db.AppDatabase;
import com.example.civicfix.model.FeedItem;
import com.example.civicfix.model.Report;
import com.example.civicfix.network.ConnectivityReceiver;
import com.example.civicfix.ui.ReportActivity;
import com.example.civicfix.ui.IssueDetailActivity;
import com.example.civicfix.util.NetworkUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CivicFix";

    private ActivityMainBinding binding;
    private ReportAdapter adapter;
    private ListenerRegistration feedListener;

    private List<FeedItem> allItems = new ArrayList<>();
    private String selectedDept = null;    // null means "All"
    private String selectedStatus = null;  // null means "All"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        adapter = new ReportAdapter(this::toggleVote);
        adapter.setOnOpenListener(item -> {
            Intent intent = new Intent(this, IssueDetailActivity.class);
            intent.putExtra(IssueDetailActivity.EXTRA_ID, item.id);
            startActivity(intent);
        });
        binding.rvFeed.setLayoutManager(new LinearLayoutManager(this));
        binding.rvFeed.setAdapter(adapter);

        binding.btnNewReport.setOnClickListener(v ->
                startActivity(new Intent(this, ReportActivity.class)));

        // Filter chips
        setupChips(binding.rowDept,
                new String[]{"All departments", "Roads", "Power", "Water", "Sanitation"},
                new String[]{null, Report.CAT_ROADS, Report.CAT_POWER,
                        Report.CAT_WATER, Report.CAT_SANITATION},
                value -> {
                    selectedDept = value;
                    applyFilter();
                });

        setupChips(binding.rowStatus,
                new String[]{"Any status", "🟡 Submitted", "🔵 Under Inspection", "🟢 Resolved"},
                new String[]{null, "Submitted", "Under Inspection", "Resolved"},
                value -> {
                    selectedStatus = value;
                    applyFilter();
                });

        // Reports saved on this phone that have not been uploaded yet
        AppDatabase.getInstance(this).reportDao().getPendingCountLive()
                .observe(this, count -> {
                    if (count != null && count > 0) {
                        binding.tvPending.setText("⏳ " + count
                                + (count == 1 ? " report is" : " reports are")
                                + " waiting to upload");
                        binding.tvPending.setVisibility(View.VISIBLE);
                    } else {
                        binding.tvPending.setVisibility(View.GONE);
                    }
                });

        // Offline banner
        ConnectivityReceiver.online.observe(this, isOnline ->
                binding.tvOffline.setVisibility(
                        Boolean.TRUE.equals(isOnline) ? View.GONE : View.VISIBLE));
    }

    @Override
    protected void onStart() {
        super.onStart();
        ensureSignedIn();
        startFeedListener();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (feedListener != null) {
            feedListener.remove();
            feedListener = null;
        }
    }

    // ---------------- Identity (anonymous sign-in) ----------------

    private void ensureSignedIn() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            adapter.setCurrentUid(user.getUid());
            return;
        }
        auth.signInAnonymously()
                .addOnSuccessListener(result ->
                        adapter.setCurrentUid(result.getUser().getUid()))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Anonymous sign-in failed", e));
    }

    // ---------------- Upvoting ----------------

    private void toggleVote(FeedItem item) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Signing you in. Try again in a moment.",
                    Toast.LENGTH_SHORT).show();
            ensureSignedIn();
            return;
        }
        if (!NetworkUtil.isOnline(this)) {
            Toast.makeText(this, "Connect to the internet to upvote.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference ref = db.collection("reports").document(item.id);

        // A transaction reads the current voters first, so two people voting at the
        // same moment can never overwrite each other, and one person never counts twice.
        db.runTransaction(tx -> {
            DocumentSnapshot snap = tx.get(ref);
            List<String> voters = new ArrayList<>();
            Object raw = snap.get("voters");
            if (raw instanceof List) {
                for (Object o : (List<?>) raw) voters.add(String.valueOf(o));
            }

            if (voters.contains(uid)) {
                voters.remove(uid);   // tapping again removes your vote
            } else {
                voters.add(uid);
            }

            Map<String, Object> update = new HashMap<>();
            update.put("voters", voters);
            update.put("upvotes", voters.size());
            tx.update(ref, update);
            return null;
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Vote failed", e);
            Toast.makeText(this, "Vote failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        });
    }

    // ---------------- Live feed ----------------

    private void startFeedListener() {
        if (feedListener != null) return;

        feedListener = FirebaseFirestore.getInstance()
                .collection("reports")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Feed listener failed", error);
                        Toast.makeText(this, "Feed error: " + error.getMessage(),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (snapshot == null) return;

                    Log.d(TAG, "Feed snapshot: " + snapshot.size()
                            + " docs, fromCache=" + snapshot.getMetadata().isFromCache());

                    List<FeedItem> items = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        items.add(FeedItem.from(doc));
                    }
                    Collections.sort(items, FeedItem.PRIORITY_ORDER);

                    allItems = items;
                    applyFilter();
                });
    }

    // ---------------- Filtering ----------------

    private void applyFilter() {
        List<FeedItem> shown = new ArrayList<>();
        for (FeedItem f : allItems) {
            if (selectedDept != null && !selectedDept.equals(f.category)) continue;
            if (selectedStatus != null && !selectedStatus.equals(f.status)) continue;
            shown.add(f);
        }
        adapter.submitList(shown);

        if (shown.isEmpty()) {
            binding.tvEmpty.setText(allItems.isEmpty()
                    ? "No reports yet.\nBe the first to report a hazard."
                    : "No reports match these filters.");
            binding.tvEmpty.setVisibility(View.VISIBLE);
        } else {
            binding.tvEmpty.setVisibility(View.GONE);
        }
    }

    private void setupChips(LinearLayout row, String[] labels, String[] values,
                            Consumer<String> onSelect) {
        List<TextView> chips = new ArrayList<>();

        for (int i = 0; i < labels.length; i++) {
            TextView chip = new TextView(this);
            chip.setText(labels[i]);
            chip.setTextSize(13);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            chip.setBackgroundResource(R.drawable.bg_chip);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setLayoutParams(lp);

            final String value = values[i];
            chip.setOnClickListener(v -> {
                for (TextView c : chips) {
                    boolean selected = (c == v);
                    c.setSelected(selected);
                    c.setTextColor(selected ? Color.WHITE : 0xFF0D47A1);
                }
                onSelect.accept(value);
            });

            chips.add(chip);
            row.addView(chip);
        }

        // The first chip ("All") starts selected
        chips.get(0).setSelected(true);
        chips.get(0).setTextColor(Color.WHITE);
        for (int i = 1; i < chips.size(); i++) chips.get(i).setTextColor(0xFF0D47A1);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}