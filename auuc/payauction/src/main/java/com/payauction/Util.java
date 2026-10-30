package com.payauction;

import java.util.Locale;

public final class Util {
    private Util() {
    }

    /** Wandelt "1000", "1,500.5", "5k", "2.5m" in eine Zahl um. -1 bei Fehler. */
    public static double parseAmount(String raw) {
        if (raw == null) return -1;
        String s = raw.trim().replace(",", "").replace(" ", "");
        if (s.isEmpty()) return -1;
        double mult = 1;
        char last = Character.toLowerCase(s.charAt(s.length() - 1));
        if (last == 'k') mult = 1_000;
        else if (last == 'm') mult = 1_000_000;
        else if (last == 'b') mult = 1_000_000_000;
        if (mult != 1) s = s.substring(0, s.length() - 1);
        try {
            double v = Double.parseDouble(s) * mult;
            return Double.isFinite(v) ? v : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /** 5000 -> "5,000", 12.5 -> "12.50" */
    public static String fmt(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return String.format(Locale.US, "%,d", (long) v);
        return String.format(Locale.US, "%,.2f", v);
    }
}
