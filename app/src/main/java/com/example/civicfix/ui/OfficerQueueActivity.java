package com.example.civicfix.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.civicfix.adapter.ReportAdapter;
import com.example.civicfix.databinding.ActivityOfficerQueueBinding;
import com.example.civicfix.model.FeedItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class OfficerQueueActivity extends AppCompatActivity {

    private static final String TAG = "CivicFix";

    /** Most urgent first: high urgency, then Critical > Medium > Low, then most votes, then oldest. */
    private static final Comparator<FeedItem> QUEUE_ORDER = (a, b) -> {
        if (a.isHighUrgency() != b.isHighUrgency()) return a.isHighUrgency() ? -1 : 1;
        int sa = severityRank(a.severity);
        int sb = severityRank(b.severity);
        if (sa != sb) return Integer.compare(sa, sb);
        if (a.upvotes != b.upvotes) return Long.compare(b.upvotes, a.upvotes);
        return Long.compare(a.createdAt, b.createdAt);
    };

    private static int severityRank(String severity) {
        if ("Critical".equals(severity)) return 0;
        if ("Low".equals(severity)) return 2;
        return 1;
    }

    private ActivityOfficerQueueBinding b;
    private ReportAdapter adapter;
    private ListenerRegistration registration;
    private String department;
    private List<FeedItem> allItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityOfficerQueueBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setTitle("Officer queue");

        adapter = new ReportAdapter(item -> Toast.makeText(this,
                "Voting is for citizens. Open the report to update it.",
                Toast.LENGTH_SHORT).show());
        adapter.setOnOpenListener(item -> {
            Intent intent = new Intent(this, IssueDetailActivity.class);
            intent.putExtra(IssueDetailActivity.EXTRA_ID, item.id);
            startActivity(intent);
        });
        b.rvQueue.setLayoutManager(new LinearLayoutManager(this));
        b.rvQueue.setAdapter(adapter);

        b.btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            finish();
        });
        b.cbShowResolved.setOnCheckedChangeListener((v, checked) -> applyFilter());

        loadOfficer();
    }

    @Override
    protected void onStart() {
        super.onStart();
        startListener();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    private void loadOfficer() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.isAnonymous()) {
            startActivity(new Intent(this, OfficerLoginActivity.class));
            finish();
            return;
        }

        FirebaseFirestore.getInstance().collection("officers").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists() || doc.getString("department") == null) {
                        FirebaseAuth.getInstance().signOut();
                        Toast.makeText(this, "This account is not a registered officer.",
                                Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                    department = doc.getString("department");
                    b.tvDepartment.setText("Officer · " + department);
                    startListener();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Officer lookup failed", e);
                    Toast.makeText(this, "Could not load your officer profile.",
                            Toast.LENGTH_LONG).show();
                });
    }

    private void startListener() {
        if (department == null || registration != null) return;

        registration = FirebaseFirestore.getInstance()
                .collection("reports")
                .whereEqualTo("category", department)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Queue listener failed", error);
                        return;
                    }
                    if (snapshot == null) return;

                    List<FeedItem> items = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        items.add(FeedItem.from(doc));
                    }
                    allItems = items;
                    applyFilter();
                });
    }

    private void applyFilter() {
        boolean showResolved = b.cbShowResolved.isChecked();
        List<FeedItem> shown = new ArrayList<>();
        int open = 0;
        int urgent = 0;

        for (FeedItem f : allItems) {
            boolean resolved = "Resolved".equals(f.status);
            if (!resolved) {
                open++;
                if (f.isHighUrgency()) urgent++;
            }
            if (resolved && !showResolved) continue;
            shown.add(f);
        }
        Collections.sort(shown, QUEUE_ORDER);
        adapter.submitList(shown);

        b.tvSummary.setText(open + " open · " + urgent + " high urgency");
        b.tvEmpty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }
}