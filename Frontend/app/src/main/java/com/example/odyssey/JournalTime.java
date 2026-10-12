package com.example.odyssey;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Presentation only. Preserve the supplied local clock/date, including date-line crossings. */
final class JournalTime {
    static String date(String iso) { return date(iso, "d MMM"); }
    static String date(String iso, String pattern) {
        if (iso == null || iso.length() < 10) return "Date pending";
        SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        input.setLenient(false); input.setTimeZone(TimeZone.getTimeZone("UTC"));
        String part = iso.substring(0, 10);
        ParsePosition position = new ParsePosition(0);
        Date value = input.parse(part, position);
        if (value == null || position.getIndex() != part.length()) return "Date pending";
        SimpleDateFormat output = new SimpleDateFormat(pattern, Locale.ENGLISH);
        output.setTimeZone(TimeZone.getTimeZone("UTC"));
        return output.format(value);
    }
    static String clock(String iso) {
        if (iso == null || !iso.matches(".+T([01][0-9]|2[0-3]):[0-5][0-9].*")) return "—";
        int start = iso.indexOf('T') + 1;
        return iso.substring(start, start + 5);
    }
    static String duration(int minutes) {
        return minutes < 60 ? minutes + " min" : minutes / 60 + "h" + (minutes % 60 == 0 ? "" : " " + minutes % 60 + "m");
    }
    private JournalTime() {}
}
