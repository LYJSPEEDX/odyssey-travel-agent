package com.example.odyssey;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 表单的“纯日期”工具：例如 20261110 表示 2026-11-10，不包含小时或地点时区。
 * 日期运算统一在 UTC 进行，避免夏令时某天不是 24 小时导致天数算错。
 */
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
        // 首尾两天都计入：同一天往返为 1 天，而不是 0 天；不表示实际航程经过的小时数。
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
