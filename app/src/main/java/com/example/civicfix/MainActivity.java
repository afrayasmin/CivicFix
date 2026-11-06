package com.example.civicfix;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.civicfix.adapter.ReportAdapter;
import com.example.civicfix.databinding.ActivityMainBinding;
import com.example.civicfix.db.AppDatabase;
import com.example.civicfix.model.FeedItem;
import com.example.civicfix.network.ConnectivityReceiver;
import com.example.civicfix.ui.ReportActivity;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CivicFix";

    private ActivityMainBinding binding;
    private ReportAdapter adapter;
    private ListenerRegistration feedListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        adapter = new ReportAdapter();
        binding.rvFeed.setLayoutManager(new LinearLayoutManager(this));
        binding.rvFeed.setAdapter(adapter);

        binding.btnNewReport.setOnClickListener(v ->
                startActivity(new Intent(this, ReportActivity.class)));

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

                    adapter.submitList(items);
                    binding.tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }
}