package com.sahyatri.guide.service;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Short names for mountaineering institutes, for the stats row and the link card ("NIM & ABVIMAS trained"). An
 * acronym in the issuer wins ("ABVIMAS, Manali", "Himalayan Mountaineering Institute (HMI)"); then the known names;
 * else the part before the first comma.
 */
public final class InstituteNames {

    private static final Pattern ACRONYM = Pattern.compile("\\b[A-Z][A-Z&]{2,}\\b");
    private static final Map<String, String> KNOWN = Map.of(
            "nehru institute of mountaineering", "NIM",
            "atal bihari vajpayee institute", "ABVIMAS",
            "himalayan mountaineering institute", "HMI",
            "jawahar institute of mountaineering", "JIM&WS");

    private InstituteNames() {
    }

    /** Null for a blank issuer. */
    public static String shortName(String issuer) {
        if (issuer == null || issuer.isBlank()) {
            return null;
        }
        Matcher m = ACRONYM.matcher(issuer);
        if (m.find()) {
            return m.group();
        }
        String lower = issuer.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : KNOWN.entrySet()) {
            if (lower.contains(e.getKey())) {
                return e.getValue();
            }
        }
        int comma = issuer.indexOf(',');
        return (comma > 0 ? issuer.substring(0, comma) : issuer).strip();
    }
}
