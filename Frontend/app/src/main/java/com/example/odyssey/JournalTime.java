package com.example.odyssey;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 只负责时间的可读展示，保留 JSON 中给出的当地日期和钟表时间，不转成手机所在时区。
 * 例如跨日期变更线的航班可能显示“到达早于出发”；航程时长直接读取 durationMinutes。
 */
final class JournalTime {
    static String date(String iso) { return date(iso, "d MMM"); }
    static String date(String iso, String pattern) {
        // 只取前十位日期，统一用 UTC 做日期格式化，避免手机时区让日期前移或后移。
        // 此处并不是把航班时刻转换为 UTC，也不验证完整时间戳与 timeZone 是否一致。
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
        // 仅检查并取出 T 后的 HH:mm；缺少时间时显示横线，不伪造为 00:00。
        if (iso == null || !iso.matches(".+T([01][0-9]|2[0-3]):[0-5][0-9].*")) return "—";
        int start = iso.indexOf('T') + 1;
        return iso.substring(start, start + 5);
    }
    static String duration(int minutes) {
        return minutes < 60 ? minutes + " min" : minutes / 60 + "h" + (minutes % 60 == 0 ? "" : " " + minutes % 60 + "m");
    }
    private JournalTime() {}
}
