package com.example.civicfix.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.civicfix.databinding.ActivityEmergencyBinding;
import com.example.civicfix.util.CivicConfig;

public class EmergencyActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityEmergencyBinding b = ActivityEmergencyBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        setTitle("Emergency");
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        for (String[] hotline : CivicConfig.HOTLINES) {
            String label = hotline[0];
            String number = hotline[1];

            Button button = new Button(this);
            button.setAllCaps(false);
            button.setTextSize(16);
            button.setMinHeight(dp(72));
            button.setText(label + "\n"
                    + (CivicConfig.isConfigured(number) ? number : "Number not set yet"));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(10);
            button.setLayoutParams(lp);

            button.setOnClickListener(v -> dial(number));
            b.llHotlines.addView(button);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void dial(String number) {
        if (!CivicConfig.isConfigured(number)) {
            Toast.makeText(this, "This number has not been set yet.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            // ACTION_DIAL only opens the dialer, so no CALL_PHONE permission is needed
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No dialer app found", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}