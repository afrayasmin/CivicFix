package com.example.civicfix.util;

public class CivicConfig {

    // Replace with the real grievance address of your city council.
    // For testing, use your own email address.
    public static final String COUNCIL_EMAIL = "afrayasmin98@gmail.com";

    // {label, phone number}. Tapping a button opens the dialer with the number filled in.
    // Defaults are for Bangladesh (Khulna). Check them before you rely on them.
    public static final String[][] HOTLINES = {
            {"🚨 National Emergency (Police, Fire, Ambulance)", "999"},
            {"🔥 Fire Service and Civil Defence", "102"},
            {"⚡ Electricity Hazard Desk (national power hotline)", "16999"},
            {"💧 Water Emergency Line (Khulna WASA, 24/7)", "01999445566"},
            {"🏛️ Municipal Grievance Helpline (National Helpline 333)", "333"}
    };

    // A number that starts with "REPLACE" shows "Number not set yet"
    public static boolean isConfigured(String number) {
        return number != null && !number.startsWith("REPLACE");
    }
}