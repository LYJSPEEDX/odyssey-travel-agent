package com.example.odyssey;

import android.content.Context;
import java.util.Arrays;
import java.util.List;

/** 首页灵感示例，只用于浏览，不自动写入用户的 My Trips 行程库。 */
final class ExampleTrips {
    static List<SavedTrip> all(Context context) {
        // 列表只需要摘要；结构化示例的完整 JSON 在 get 中按需加载。
        return Arrays.asList(
                example(context, "kyoto", "Kyoto", 3, R.string.sample_kyoto_title,
                        R.string.sample_kyoto_meta, R.string.sample_kyoto_detail),
                example(context, "tokyo", "Tokyo", 5, R.string.sample_tokyo_title,
                        R.string.sample_tokyo_meta, R.string.sample_tokyo_detail),
                example(context, "bali", "Bali", 4, R.string.sample_bali_title,
                        R.string.sample_bali_meta, R.string.sample_bali_detail),
                new SavedTrip("contract_canberra", 0, "Canberra", "Sydney → Canberra", "3 days · Drive or bus", 3, "", ""),
                new SavedTrip("contract_new_york", 0, "New York", "Sydney → New York", "10 days · Flights & stopovers", 10, "", ""));
    }
    static SavedTrip get(Context context, String id) throws org.json.JSONException {
        // 两个 contract 示例演示可变天数和交通方式，其余示例沿用旧版文本详情。
        if (id.equals("contract_canberra") || id.equals("contract_new_york")) {
            String raw = ItineraryAssets.example(context, id);
            org.json.JSONObject root = new org.json.JSONObject(raw), summary = root.getJSONObject("summary");
            return new SavedTrip(id, 0, summary.getString("title"), summary.getString("title"),
                    summary.getString("startDate") + " → " + summary.getString("endDate"),
                    summary.getInt("dayCount"), summary.getString("subtitle"), "", raw);
        }
        for (SavedTrip trip : all(context)) if (trip.id.equals(id)) return trip;
        return null;
    }
    private static SavedTrip example(Context context, String id, String city, int days,
                                     int title, int meta, int detail) {
        return new SavedTrip(id, 0, city, context.getString(title), context.getString(meta),
                days, context.getString(R.string.sample_description), context.getString(detail));
    }
    private ExampleTrips() {}
}
