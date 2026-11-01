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
 * 重名城市（消歧义页）和地图、旗帜类配图一律不用，保留占位图，宁缺毋错。
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

    private static String lookup(String city) throws Exception {
        String title = URLEncoder.encode(city.replace(' ', '_'), "UTF-8").replace("+", "%20");
        HttpURLConnection connection = (HttpURLConnection) new URL("https://en.wikipedia.org/api/rest_v1/page/summary/" + title).openConnection();
        try {
            connection.setConnectTimeout(5000); connection.setReadTimeout(8000);
            connection.setRequestProperty("User-Agent", SafeImageLoader.USER_AGENT);
            connection.setRequestProperty("Accept", "application/json");
            if (connection.getResponseCode() == 404) throw new java.io.FileNotFoundException(city);
            try (InputStream stream = connection.getInputStream(); ByteArrayOutputStream body = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int length;
                while ((length = stream.read(buffer)) != -1 && body.size() < 200_000) body.write(buffer, 0, length);
                return pick(new JSONObject(body.toString("UTF-8")));
            }
        } finally { connection.disconnect(); }
    }
    static String pick(JSONObject summary) {
        if (!"standard".equals(summary.optString("type"))) return NONE;
        JSONObject thumbnail = summary.optJSONObject("thumbnail");
        String url = thumbnail == null ? "" : thumbnail.optString("source");
        String lower = url.toLowerCase(Locale.ROOT);
        for (String bad : new String[]{"flag_of", "coat_of", "seal_of", "emblem", "locator", "_map", "map_", ".svg", "logo"})
            if (lower.contains(bad)) return NONE;
        // 摘要只给小图，换成维基媒体的标准大图尺寸。
        url = url.replaceFirst("/\\d+px-", "/960px-");
        return ImageUrlPolicy.accept(url) == null ? NONE : url;
    }
    private CityPhotos() { }
}
