package com.example.odyssey;

import android.content.Context;
import android.content.Intent;
import org.json.JSONException;

/** 用文档里的合约样本构造“已保存行程”，详情页测试不依赖后端。 */
final class TestTrips {
    static final String CANBERRA = "examples/canberra-3-days.json", NEW_YORK = "examples/new-york-10-days.json";
    static String raw(Context context, String path) throws JSONException { return ItineraryAssets.read(context, path); }
    static Intent saved(Context context, String path) {
        try {
            SavedTrip trip = SavedTrip.fromContract(context, raw(context, path));
            org.junit.Assert.assertTrue(new TripStore(context).save(trip));
            return new Intent(context, TripDetailActivity.class).putExtra(TripDetailActivity.EXTRA_ID, trip.id);
        } catch (JSONException error) { throw new AssertionError(error); }
    }
    private TestTrips() { }
}
