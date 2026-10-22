package com.example.odyssey;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
/** 媒体失败仅影响图片；文本类型一律通过公共校验边界。 */
final class ResponseImage {
    final String url, alt, credit;
    ResponseImage(String url, String alt, String credit) { this.url = url; this.alt = alt; this.credit = credit; }
    static String text(Object value, int max) {
        try { return InputValidation.optionalText(value == JSONObject.NULL ? null : value, max, "Image text"); }
        catch (IllegalArgumentException ignored) { return ""; }
    }
    static ResponseImage cover(JSONObject root, boolean thumbnail, long now) {
        if (root == null) return null;
        JSONObject summary = root.optJSONObject("summary");
        String id = summary == null ? "" : text(summary.opt("coverImageId"), 200);
        JSONArray media = root.optJSONArray("media");
        if (id.isEmpty() || media == null) return null;
        for (int i = 0; i < Math.min(media.length(), 300); i++) {
            JSONObject item = media.optJSONObject(i);
            if (item == null || !id.equals(text(item.opt("id"), 200)) || !"READY".equals(text(item.opt("status"), 30))) continue;
            if (!item.isNull("expiresAt") && !fresh(text(item.opt("expiresAt"), 100), now)) return null;
            String url = ImageUrlPolicy.accept(item.opt(thumbnail ? "thumbnailUrl" : "url"));
            if (thumbnail && url == null) url = ImageUrlPolicy.accept(item.opt("url"));
            if (url == null) return null;
            StringBuilder credit = new StringBuilder();
            JSONArray credits = item.optJSONArray("attributions");
            if (credits != null) for (int j = 0; j < Math.min(credits.length(), 10); j++) {
                JSONObject entry = credits.optJSONObject(j);
                String part = entry == null ? "" : text(entry.opt("text"), 300);
                if (!part.isEmpty()) { if (credit.length() > 0) credit.append(" · "); credit.append(part); }
            }
            return new ResponseImage(url, text(item.opt("altText"), 500), credit.toString());
        }
        return null;
    }
    private static boolean fresh(String value, long now) {
        for (String pattern : new String[]{"yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX"}) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US); format.setLenient(false);
            format.setTimeZone(TimeZone.getTimeZone("UTC")); ParsePosition position = new ParsePosition(0);
            java.util.Date date = format.parse(value, position);
            if (date != null && position.getIndex() == value.length()) return date.getTime() > now;
        }
        return false;
    }
}
