package com.example.civicfix.model;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FeedItem {

    // Reports with this many upvotes (or more) become High Urgency
    public static final int HIGH_URGENCY_VOTES = 2;

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
    public List<String> voters = new ArrayList<>();

    public boolean isHighUrgency() {
        return upvotes >= HIGH_URGENCY_VOTES;
    }

    public boolean hasVoted(String uid) {
        return uid != null && voters.contains(uid);
    }

    private static String str(DocumentSnapshot d, String key) {
        Object v = d.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static double num(DocumentSnapshot d, String key) {
        Object v = d.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        try {
            return v == null ? 0 : Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static FeedItem from(DocumentSnapshot d) {
        FeedItem f = new FeedItem();
        f.id = d.getId();
        f.category = str(d, "category");
        f.severity = str(d, "severity");
        f.description = str(d, "description");
        f.landmark = str(d, "landmark");
        f.address = str(d, "address");
        f.photoUrl = str(d, "photoUrl");

        String st = str(d, "status");
        f.status = st != null ? st : "Submitted";

        f.latitude = num(d, "latitude");
        f.longitude = num(d, "longitude");
        f.upvotes = (long) num(d, "upvotes");
        f.createdAt = (long) num(d, "createdAt");

        Object raw = d.get("voters");
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) f.voters.add(String.valueOf(o));
        }
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