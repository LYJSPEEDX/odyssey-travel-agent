package com.example.odyssey;

import android.widget.ImageView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Locale;

/**
 * 离线城市照片目录：只接受明确的城市名或 assetKey，不把模型字符串当文件路径/网络地址。
 * 复用同一目录让首页、目的地选择、Live Trip 和详情封面保持一致。
 * 远程图片由独立安全加载边界处理，本地素材始终先显示。
 */
final class TripPhotos {
    static int resource(String city) {
        // 精确匹配而非 contains：例如 Paris, Texas 不能错误使用法国巴黎照片。
        String key = InputValidation.text(city).toLowerCase(Locale.ROOT);
        switch (key) {
            case "kyoto": return R.drawable.photo_kyoto;
            case "tokyo": return R.drawable.photo_tokyo;
            case "bali": return R.drawable.photo_bali;
            case "paris": return R.drawable.photo_paris;
            case "new york": return R.drawable.photo_new_york;
            case "canberra": return R.drawable.photo_canberra;
            default: return R.drawable.bg_photo_placeholder;
        }
    }

    /** 本地快照兼容：从 contract 的地点 ID 解析城市，不误用整段行程标题作为城市名。 */
    static String destination(SavedTrip trip) {
        if (trip.contractJson != null) try {
            JSONObject root = new JSONObject(trip.contractJson);
            JSONArray ids = root.getJSONObject("requestSnapshot").getJSONArray("destinationPlaceIds");
            JSONArray places = root.getJSONArray("places");
            if (ids.length() > 0) for (int i = 0; i < places.length(); i++) {
                JSONObject place = places.getJSONObject(i);
                if (ids.getString(0).equals(place.getString("id"))) return place.getString("name");
            }
        } catch (JSONException ignored) {
            // 照片缺失不能让整个列表不可用；详情入口仍会执行原有严格 JSON 校验。
        }
        return trip.destination;
    }

    static void show(ImageView view, String city) {
        SafeImageLoader.SHARED.cancel(view);
        int resource = resource(city);
        view.setImageResource(resource); // 打包资源不依赖网络；系统会复用 Bitmap drawable 的缓存。
        view.setScaleType(ImageView.ScaleType.CENTER_CROP); // 裁掉边缘，不拉伸建筑和人物。
        view.setContentDescription(view.getContext().getString(resource == R.drawable.bg_photo_placeholder
                ? R.string.editorial_no_photo : R.string.editorial_cover_alt, city));
    }

    /** 旧调用默认列表尺寸；详情明确请求完整图。 */
    static void showCover(ImageView view, JSONObject root, String fallbackCity) {
        showResponse(view, ResponseImage.cover(root, true, System.currentTimeMillis()), fallbackCity(root, fallbackCity), null);
    }
    static String fallbackCity(JSONObject root, String city) {
        JSONObject summary = root == null ? null : root.optJSONObject("summary");
        JSONArray media = root == null ? null : root.optJSONArray("media");
        String id = summary == null ? "" : ResponseImage.text(summary.opt("coverImageId"), 200);
        if (!id.isEmpty() && media != null) for (int i = 0; i < Math.min(media.length(), 300); i++) {
            JSONObject item = media.optJSONObject(i);
            if (item == null || !id.equals(ResponseImage.text(item.opt("id"), 200)) || !"READY".equals(ResponseImage.text(item.opt("status"), 30))) continue;
            String key = ResponseImage.text(item.opt("assetKey"), 100);
            for (String known : new String[]{"Kyoto", "Tokyo", "Bali", "Paris", "New York", "Canberra"})
                if (("city_" + known.toLowerCase(Locale.ROOT).replace(' ', '_')).equals(key)) return known;
        }
        return city;
    }
    static void showResponse(ImageView view, ResponseImage image, String city, android.widget.TextView credit) {
        show(view, city);
        if (credit != null) { credit.setText(""); credit.setVisibility(android.view.View.GONE); }
        if (image == null) return;
        java.lang.ref.WeakReference<android.widget.TextView> label = new java.lang.ref.WeakReference<>(credit);
        SafeImageLoader.SHARED.load(view, image.url, image.alt, () -> {
            android.widget.TextView text = label.get();
            if (text != null && !image.credit.isEmpty()) { text.setText(image.credit); text.setVisibility(android.view.View.VISIBLE); }
        });
    }

    static String keywords(String city) {
        TripOptions.Destination item = TripOptions.destination(city);
        if (item != null) return item.description();
        if ("Canberra".equalsIgnoreCase(city)) return "Museums · Lakes · Culture";
        if ("New York".equalsIgnoreCase(city)) return "Art · Skyline · Neighbourhoods";
        return "Your next chapter";
    }
    private TripPhotos() {}
}
