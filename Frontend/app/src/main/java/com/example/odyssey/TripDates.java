package com.example.odyssey;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/** Date-only keys (yyyyMMdd) avoid device timezone and DST changing selected days. */
final class TripDates {
    private TripDates() {}
    static int key(Calendar day) {
        return day.get(Calendar.YEAR) * 10000 + (day.get(Calendar.MONTH) + 1) * 100
                + day.get(Calendar.DAY_OF_MONTH);
    }
    static Calendar calendar(int key) {
        Calendar date = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        date.clear();
        date.set(key / 10000, key / 100 % 100 - 1, key % 100);
        return date;
    }
    static int daysBetweenInclusive(int start, int end) {
        return (int) ((calendar(end).getTimeInMillis() - calendar(start).getTimeInMillis())
                / 86400000L) + 1;
    }
    static String format(int key) {
        if (key == 0) return "—";
        SimpleDateFormat format = new SimpleDateFormat("d MMM yyyy", Locale.ENGLISH);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(calendar(key).getTime());
    }
}
