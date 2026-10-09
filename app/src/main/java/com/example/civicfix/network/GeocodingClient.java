package com.example.civicfix.network;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GeocodingClient {

    /**
     * Converts coordinates to a readable address.
     * Blocking call: run it on a background thread.
     * Returns null if the request fails (for example, no internet).
     */
    public static String reverse(double lat, double lon) {
        HttpURLConnection conn = null;
        try {
            String url = String.format(Locale.US,
                    "https://nominatim.openstreetmap.org/reverse"
                            + "?format=jsonv2&lat=%f&lon=%f&zoom=18&addressdetails=1", lat, lon);

            conn = (HttpURLConnection) new URL(url).openConnection();
            // Nominatim requires an identifying User-Agent
            conn.setRequestProperty("User-Agent", "CivicFix-StudentProject/1.0");
            conn.setRequestProperty("Accept-Language", "en");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }

            JSONObject json = new JSONObject(sb.toString());
            JSONObject a = json.optJSONObject("address");

            if (a != null) {
                List<String> parts = new ArrayList<>();
                addIfPresent(parts, a.optString("road", ""));
                addIfPresent(parts, firstNonEmpty(a, "neighbourhood", "suburb", "quarter"));
                addIfPresent(parts, firstNonEmpty(a, "city", "town", "village", "county"));
                if (!parts.isEmpty()) return android.text.TextUtils.join(", ", parts);
            }
            String display = json.optString("display_name", "");
            return display.isEmpty() ? null : display;

        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String firstNonEmpty(JSONObject o, String... keys) {
        for (String k : keys) {
            String v = o.optString(k, "");
            if (!v.isEmpty()) return v;
        }
        return "";
    }

    private static void addIfPresent(List<String> list, String value) {
        if (value != null && !value.isEmpty()) list.add(value);
    }
}