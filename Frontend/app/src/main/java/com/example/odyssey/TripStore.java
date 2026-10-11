package com.example.odyssey;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Small offline prototype library. Each trip has its own key; saves cannot erase other trips. */
final class TripStore {
    static final String FILE = "trip_library";
    private static final String PREFIX = "trip_";
    private final SharedPreferences preferences;

    TripStore(Context context) {
        this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE));
    }
    TripStore(SharedPreferences preferences) { this.preferences = preferences; }

    // Call off the UI thread: commit confirms the snapshot reached storage.
    boolean save(SavedTrip trip) {
        try {
            return preferences.edit().putString(PREFIX + trip.id, trip.toJson().toString()).commit();
        } catch (JSONException exception) {
            return false;
        }
    }

    List<SavedTrip> list() throws JSONException {
        List<SavedTrip> trips = new ArrayList<>();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (!entry.getKey().startsWith(PREFIX)) continue;
            if (!(entry.getValue() instanceof String)) throw new JSONException("Unreadable trip entry");
            trips.add(SavedTrip.fromJson((String) entry.getValue()));
        }
        trips.sort((left, right) -> Long.compare(right.createdAt, left.createdAt));
        return trips;
    }

    SavedTrip get(String id) throws JSONException {
        String value = preferences.getString(PREFIX + id, null);
        return value == null ? null : SavedTrip.fromJson(value);
    }
}
