package com.example.civicfix.util;

public class CivicConfig {

    // Replace with the real municipal grievance address. For testing, use your own email.
    public static final String COUNCIL_EMAIL = "afrayasmin98@gmail.com";

    // {label, phone number}. A number starting with "REPLACE" shows "not set yet".
    // 999 is Bangladesh's national emergency number. Look up the real local numbers
    // for the others (electricity, water, grievance helpline) and put them here.
    public static final String[][] HOTLINES = {
            {"🔥 Fire Department", "999"},
            {"⚡ Electricity Hazard Desk", "REPLACE_ME"},
            {"💧 Water Emergency Line", "REPLACE_ME"},
            {"🏛️ Municipal Grievance Helpline", "REPLACE_ME"}
    };

    public static boolean isConfigured(String number) {
        return number != null && !number.startsWith("REPLACE");
    }
}