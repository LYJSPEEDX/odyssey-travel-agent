package com.example.odyssey;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 没有自带照片的城市，用维基百科词条的配图（免密钥）。只发送城市名，结果缓存在本机。
 * 词条必须提到地名里的地区和国家，否则视为同名的其他地方；地图、旗帜类配图也不用，
 * 宁可保留占位图也不配错。
 */
final class CityPhotos {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String NONE = "none";

    static void load(ImageView view, String city) {
        String name = InputValidation.text(city);
        if (name.isEmpty()) return;
        view.setTag(R.id.cityPhotoRequest, name);
        Context app = view.getContext().getApplicationContext();
        SharedPreferences cache = app.getSharedPreferences("city_photos", Context.MODE_PRIVATE);
        String key = name.toLowerCase(Locale.ROOT);
        String cached = cache.getString(key, null);
        if (cached != null) { if (!cached.equals(NONE)) SafeImageLoader.SHARED.load(view, cached, ""); return; }
        java.lang.ref.WeakReference<ImageView> target = new java.lang.ref.WeakReference<>(view);
        WORKER.execute(() -> {
            String url;
            try { url = lookup(name); }
            catch (java.io.FileNotFoundException missing) { url = NONE; }
            catch (Exception offline) { return; } // 网络问题不写缓存，下次还能重试。
            String result = url;
            cache.edit().putString(key, result).apply();
            MAIN.post(() -> {
                ImageView image = target.get();
                // 控件可能已被复用或换成后端封面，令牌不一致就不覆盖。
                if (image != null && !result.equals(NONE) && name.equals(image.getTag(R.id.cityPhotoRequest)))
                    SafeImageLoader.SHARED.load(image, result, "");
            });
        });
    }
    /** 后端返回了自己的封面时调用，让还在路上的查询失效。 */
    static void forget(ImageView view) { view.setTag(R.id.cityPhotoRequest, null); }

    private static String lookup(String place) throws Exception {
        String[] parts = place.split(",");
        String title = URLEncoder.encode(parts[0].trim().replace(' ', '_'), "UTF-8").replace("+", "%20");
        JSONObject summary = get("https://en.wikipedia.org/api/rest_v1/page/summary/" + title);
        if (!matches(summary, place)) return NONE;
        String lead = pick(summary);
        if (!lead.equals(NONE)) return lead;
        // 主图是旗帜或地图时（如澳门），从词条的图片列表里找第一张照片。
        return pickFromMedia(get("https://en.wikipedia.org/api/rest_v1/page/media-list/" + title));
    }
    private static JSONObject get(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(5000); connection.setReadTimeout(8000);
            connection.setRequestProperty("User-Agent", SafeImageLoader.USER_AGENT);
            connection.setRequestProperty("Accept", "application/json");
            if (connection.getResponseCode() == 404) throw new java.io.FileNotFoundException(url);
            try (InputStream stream = connection.getInputStream(); ByteArrayOutputStream body = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int length;
                while ((length = stream.read(buffer)) != -1 && body.size() < 500_000) body.write(buffer, 0, length);
                return new JSONObject(body.toString("UTF-8"));
            }
        } finally { connection.disconnect(); }
    }
    /** "Macao, Taiwan" 不能用澳门的词条：地名逗号后的每一段，首词都要出现在词条简介里。 */
    static boolean matches(JSONObject summary, String place) {
        if (!"standard".equals(summary.optString("type"))) return false;
        String text = (summary.optString("description") + " " + summary.optString("extract")).toLowerCase(Locale.ROOT);
        String[] parts = place.split(",");
        for (int i = 1; i < parts.length; i++) {
            String word = parts[i].trim().split(" ")[0].toLowerCase(Locale.ROOT);
            if (!word.isEmpty() && !text.contains(word)) return false;
        }
        return true;
    }
    static String pick(JSONObject summary) {
        JSONObject thumbnail = summary.optJSONObject("thumbnail");
        return usable(thumbnail == null ? "" : thumbnail.optString("source"));
    }
    /** 优先选文件名像风景照的；都没有时才用第一张 JPG。 */
    static String pickFromMedia(JSONObject media) {
        org.json.JSONArray items = media.optJSONArray("items");
        String first = NONE;
        for (int i = 0; items != null && i < Math.min(items.length(), 40); i++) {
            JSONObject item = items.optJSONObject(i);
            org.json.JSONArray sources = item == null ? null : item.optJSONArray("srcset");
            if (!"image".equals(item == null ? "" : item.optString("type")) || sources == null || sources.length() == 0) continue;
            String source = sources.optJSONObject(0).optString("src");
            String url = usable(source.startsWith("//") ? "https:" + source : source);
            String lower = url.toLowerCase(Locale.ROOT);
            if (url.equals(NONE) || !(lower.contains(".jpg") || lower.contains(".jpeg"))) continue;
            for (String scenic : new String[]{"aerial", "skyline", "panorama", "cityscape", "view", "downtown", "harbour", "harbor", "beach", "night"})
                if (lower.contains(scenic)) return url;
            if (first.equals(NONE)) first = url;
        }
        return first;
    }
    private static String usable(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        for (String bad : new String[]{"flag_of", "coat_of", "seal_of", "emblem", "locator", "_map", "map_", "-map", ".svg", "logo", "montage", "collage", ".tif", "lossy-page", "unknown_artist"})
            if (lower.contains(bad)) return NONE;
        // 摘要只给小图，换成维基媒体的标准大图尺寸。
        url = url.replaceFirst("/\\d+px-", "/960px-");
        return ImageUrlPolicy.accept(url) == null ? NONE : url;
    }
    private CityPhotos() { }
}
