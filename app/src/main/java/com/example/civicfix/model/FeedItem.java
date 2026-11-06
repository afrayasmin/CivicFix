package com.example.civicfix.model;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Comparator;

public class FeedItem {

    // Reports with this many upvotes (or more) become High Urgency
    public static final int HIGH_URGENCY_VOTES = 5;

    public String id;
    public String category;
    public String severity;
    public String description;
    public String landmark;
    public String address;
    public String photoUrl;
    public String status;
    public double latitude;
    public double longitude;
    public long upvotes;
    public long createdAt;

    public boolean isHighUrgency() {
        return upvotes >= HIGH_URGENCY_VOTES;
    }

    public static FeedItem from(DocumentSnapshot d) {
        FeedItem f = new FeedItem();
        f.id = d.getId();
        f.category = d.getString("category");
        f.severity = d.getString("severity");
        f.description = d.getString("description");
        f.landmark = d.getString("landmark");
        f.address = d.getString("address");
        f.photoUrl = d.getString("photoUrl");

        String st = d.getString("status");
        f.status = st != null ? st : "Submitted";

        Double lat = d.getDouble("latitude");
        Double lng = d.getDouble("longitude");
        f.latitude = lat != null ? lat : 0;
        f.longitude = lng != null ? lng : 0;

        Long up = d.getLong("upvotes");
        Long ts = d.getLong("createdAt");
        f.upvotes = up != null ? up : 0;
        f.createdAt = ts != null ? ts : 0;
        return f;
    }

    /** High-urgency reports first (most upvotes on top), then newest first. */
    public static final Comparator<FeedItem> PRIORITY_ORDER = (a, b) -> {
        if (a.isHighUrgency() != b.isHighUrgency()) {
            return a.isHighUrgency() ? -1 : 1;
        }
        if (a.isHighUrgency() && a.upvotes != b.upvotes) {
            return Long.compare(b.upvotes, a.upvotes);
        }
        return Long.compare(b.createdAt, a.createdAt);
    };
}