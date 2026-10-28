package com.example.odyssey;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 读取打包进 APK 的 Schema 与示例 JSON，不进行网络请求。
 * 构建时由 docs/itinerary-contract 同步资源，后端说明与 App 不必维护两份示例。
 */
final class ItineraryAssets {
    static final int MAX_BYTES = 2_000_000;
    static String read(Context context, String path) throws JSONException {
        // try-with-resources 自动关闭流；边读边限制字节数，避免一次读入过大的资源。
        try (InputStream input = context.getAssets().open(path);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = input.read(buffer)) != -1) {
                if (output.size() + length > MAX_BYTES) throw new IOException("Asset too large");
                output.write(buffer, 0, length);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException error) { throw new JSONException("Cannot load itinerary asset"); }
    }
    static String example(Context context, String id) throws JSONException {
        String raw, city;
        // 只允许预设示例 ID 映射到固定路径，不把外部 ID 直接当作资源路径打开。
        switch (id) {
            case "contract_canberra": raw = read(context, "examples/canberra-3-days.json"); city = "Canberra"; break;
            case "contract_new_york": raw = read(context, "examples/new-york-10-days.json"); city = "New York"; break;
            default: throw new JSONException("Unknown example");
        }
        JSONObject root = new JSONObject(raw);
        JSONObject photo = MockPhotoCatalog.media(city);
        String coverId = root.getJSONObject("summary").getString("coverImageId");
        org.json.JSONArray media = root.getJSONArray("media");
        for (int i = 0; i < media.length(); i++) {
            JSONObject existing = media.getJSONObject(i);
            if (coverId.equals(existing.getString("id"))) {
                photo.put("id", coverId).put("placeId", existing.opt("placeId"));
                media.put(i, photo); return root.toString();
            }
        }
        photo.put("id", coverId); media.put(photo); return root.toString();
    }
    static JSONObject schema(Context context, String version) throws JSONException {
        if ("1.0".equals(version)) return new JSONObject(read(context, "itinerary.schema.json"));
        if ("2.0".equals(version)) return new JSONObject(read(context, "frontend-itinerary-v2.schema.json"));
        throw new JSONException("Unsupported itinerary version");
    }
    private ItineraryAssets() {}
}
