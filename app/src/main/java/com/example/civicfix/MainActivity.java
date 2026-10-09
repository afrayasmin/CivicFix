package com.example.civicfix;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.civicfix.databinding.ActivityMainBinding;
import com.example.civicfix.db.AppDatabase;
import com.example.civicfix.network.ConnectivityReceiver;
import com.example.civicfix.ui.ReportActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnNewReport.setOnClickListener(v ->
                startActivity(new Intent(this, ReportActivity.class)));

        AppDatabase.getInstance(this).reportDao().getPendingCountLive()
                .observe(this, count ->
                        binding.tvPending.setText(count + " reports waiting to upload"));

        ConnectivityReceiver.online.observe(this, isOnline ->
                binding.tvOffline.setVisibility(
                        Boolean.TRUE.equals(isOnline) ? View.GONE : View.VISIBLE));
    }
}