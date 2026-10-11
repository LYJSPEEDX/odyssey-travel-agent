package com.example.odyssey;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import java.util.UUID;
import org.json.JSONException;

@RunWith(AndroidJUnit4.class)
public class TripStoreTest {
    @Test public void snapshotsSurviveNewStoreAndSameIdDoesNotDuplicate() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String file = "test_trips_" + UUID.randomUUID();
        SharedPreferences preferences = context.getSharedPreferences(file, Context.MODE_PRIVATE);
        try {
            TripStore store = new TripStore(preferences);
            assertTrue(store.list().isEmpty());
            SavedTrip first = new SavedTrip("one", 1, "Kyoto", "Sydney → Kyoto", "Flexible",
                    3, "Vegetarian\nAvoid stairs", "Day 1\nRest");
            assertTrue(store.save(first));
            assertTrue(store.save(first));
            assertTrue(store.save(new SavedTrip("two", 2, "Tokyo", "Sydney → Tokyo",
                    "Tomorrow", 5, "Solo", "Day 1\nExplore")));
            List<SavedTrip> reopened = new TripStore(preferences).list();
            assertEquals(2, reopened.size());
            assertEquals("two", reopened.get(0).id);
            assertEquals("Vegetarian\nAvoid stairs", reopened.get(1).brief);
            assertEquals("Day 1\nRest", reopened.get(1).itinerary);
            assertEquals(3, reopened.get(1).totalDays);
        } finally {
            context.deleteSharedPreferences(file); // Only this test's isolated fixture.
        }
    }

    @Test public void corruptRecordIsReportedAndNotOverwritten() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String file = "test_trips_" + UUID.randomUUID();
        SharedPreferences preferences = context.getSharedPreferences(file, Context.MODE_PRIVATE);
        try {
            assertTrue(preferences.edit().putString("trip_broken", "not-json").commit());
            try {
                new TripStore(preferences).list();
                fail("Corrupt storage must not be presented as an empty library");
            } catch (JSONException expected) {
                assertEquals("not-json", preferences.getString("trip_broken", ""));
            }
        } finally {
            context.deleteSharedPreferences(file);
        }
    }
}
