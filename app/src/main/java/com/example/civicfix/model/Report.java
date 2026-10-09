package com.example.civicfix.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "reports")
public class Report {

    // --- Constants (kept as Strings so Room stores them easily) ---
    public static final String SYNC_PENDING = "PENDING_SYNC";
    public static final String SYNC_DONE = "SYNCED";

    public static final String CAT_ROADS = "Roads & Transport";
    public static final String CAT_POWER = "Power & Electricity";
    public static final String CAT_WATER = "Water & Sewage";
    public static final String CAT_SANITATION = "Sanitation & Waste";

    public static final String SEV_LOW = "Low";
    public static final String SEV_MEDIUM = "Medium";
    public static final String SEV_CRITICAL = "Critical";

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String cloudId;          // Firestore document id, set after sync
    public String category;
    public String severity;
    public String description;
    public String landmark;

    public double latitude;
    public double longitude;
    public String address;          // from reverse geocoding

    public String localPhotoPath;   // file path on the phone
    public String photoUrl;         // Firebase Storage URL, set after sync

    @NonNull
    public String syncState = SYNC_PENDING;

    public long createdAt;          // System.currentTimeMillis()
}