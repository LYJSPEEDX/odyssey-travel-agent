package com.example.odyssey;

import android.widget.ImageView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Locale;

/**
 * 离线城市照片目录：只接受明确的城市名或 assetKey，不把模型字符串当文件路径/网络地址。
 * 复用同一目录让首页、目的地选择、Live Trip 和详情封面保持一致。
 * 后续接远程图片可替换此显示边界；本轮不增加网络权限，也不改最终 JSON 协议。
 */
final class TripPhotos {
    static int resource(String city) {
        // 精确匹配而非 contains：例如 Paris, Texas 不能错误使用法国巴黎照片。
        String key = city == null ? "" : city.trim().toLowerCase(Locale.ROOT);
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
        int resource = resource(city);
        view.setImageResource(resource); // 打包资源不依赖网络；系统会复用 Bitmap drawable 的缓存。
        view.setScaleType(ImageView.ScaleType.CENTER_CROP); // 裁掉边缘，不拉伸建筑和人物。
        view.setContentDescription(view.getContext().getString(resource == R.drawable.bg_photo_placeholder
                ? R.string.editorial_no_photo : R.string.editorial_cover_alt, city));
    }

    /** 仅使用 READY 且列入白名单的本地 assetKey；不尝试访问任意 url 或 file URI。 */
    static void showCover(ImageView view, JSONObject root, String fallbackCity) {
        JSONObject summary = root.optJSONObject("summary");
        JSONArray media = root.optJSONArray("media");
        String id = summary == null ? "" : summary.optString("coverImageId", "");
        if (media != null) for (int i = 0; i < media.length(); i++) {
            JSONObject item = media.optJSONObject(i);
            if (item == null || !id.equals(item.optString("id")) || !"READY".equals(item.optString("status"))) continue;
            String key = item.optString("assetKey", "");
            // 目录 key 与文档一一对应，禁止资源名猜测和路径穿越。
            for (String city : new String[]{"Kyoto", "Tokyo", "Bali", "Paris", "New York", "Canberra"}) {
                if (("city_" + city.toLowerCase(Locale.ROOT).replace(' ', '_')).equals(key)) {
                    show(view, city); return;
                }
            }
        }
        show(view, fallbackCity);
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
