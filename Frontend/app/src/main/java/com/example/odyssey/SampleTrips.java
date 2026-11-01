package com.example.odyssey;

import android.content.Context;
import java.util.Arrays;
import java.util.List;

/** 首页的灵感样例：只用于浏览，不会写入 My Trips，也不来自后端。 */
final class SampleTrips {
    static List<SavedTrip> all(Context context) {
        return Arrays.asList(
                sample(context, "sample_kyoto", "Kyoto", 3, R.string.sample_kyoto_title, R.string.sample_kyoto_meta, R.string.sample_kyoto_detail),
                sample(context, "sample_tokyo", "Tokyo", 5, R.string.sample_tokyo_title, R.string.sample_tokyo_meta, R.string.sample_tokyo_detail),
                sample(context, "sample_bali", "Bali", 4, R.string.sample_bali_title, R.string.sample_bali_meta, R.string.sample_bali_detail));
    }
    static SavedTrip get(Context context, String id) {
        for (SavedTrip trip : all(context)) if (trip.id.equals(id)) return trip;
        return null;
    }
    private static SavedTrip sample(Context context, String id, String city, int days, int title, int meta, int detail) {
        return new SavedTrip(id, 0, city, context.getString(title), context.getString(meta), days, "", context.getString(detail));
    }
    private SampleTrips() { }
}
