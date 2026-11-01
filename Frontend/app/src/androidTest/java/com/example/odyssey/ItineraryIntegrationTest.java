package com.example.odyssey;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ItineraryIntegrationTest {
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }


    @Test public void legacySnapshotsRemainReadable() throws Exception {
        SavedTrip old = new SavedTrip("legacy", 1, "Kyoto", "Sydney → Kyoto", "Flexible", 3, "Brief", "Old result");
        SavedTrip read = SavedTrip.fromJson(old.toJson().toString());
        assertEquals("Old result", read.itinerary);
    }

    @Test public void examplesRenderEveryDayAndRejectBrokenContracts() throws Exception {
        for (String path : new String[]{TestTrips.CANBERRA, TestTrips.NEW_YORK}) {
            String raw = TestTrips.raw(context(), path);
            ItineraryDocument document = ItineraryDocument.parse(context(), raw);
            int days = document.root.getJSONArray("days").length();
            Intent intent = TestTrips.saved(context(), path);
            try (ActivityScenario<TripDetailActivity> scenario = ActivityScenario.launch(intent)) {
                for (int i = 0; i < days; i++) {
                    final int index = i;
                    scenario.onActivity(activity -> {
                        View content = activity.findViewById(R.id.tripDetailContent);
                        View tab = content.findViewWithTag("contract-day-" + index);
                        assertNotNull(tab);
                        tab.performClick();
                        assertTrue(contains(content, "Estimated budget"));
                        assertTrue(contains(content, "Where to stay"));
                        assertFalse(contains(content, activity.getString(R.string.trip_read_error)));
                    });
                }
            }
            JSONObject broken = new JSONObject(raw).put("schemaVersion", "999");
            reject(broken);
            broken = new JSONObject(raw).put("unrecognized", true); reject(broken);
            broken = new JSONObject(raw);
            broken.getJSONObject("summary").put("dayCount", 59); reject(broken);
            broken = new JSONObject(raw);
            broken.getJSONObject("summary").put("coverImageId", "missing-photo"); reject(broken);
            broken = new JSONObject(raw);
            broken.getJSONArray("days").getJSONObject(0).getJSONArray("events").getJSONObject(0).put("type", "SCRIPT");
            reject(broken);
        }
    }

    @Test public void recommendationsDoNotOverridePlanningOption() throws Exception {
        ItineraryDocument doc = ItineraryDocument.parse(context(), TestTrips.raw(context(), TestTrips.CANBERRA));
        JSONObject group = doc.root.getJSONArray("transportGroups").getJSONObject(0);
        String planned = group.getString("planningOptionId");
        group.put("userSelectedOptionId", JSONObject.NULL);
        assertEquals(planned, doc.selected(group).getString("id"));
        group.put("planningOptionId", JSONObject.NULL);
        assertNull(doc.selected(group));
    }

    @Test public void externalContractBecomesPersistableSnapshot() throws Exception {
        SavedTrip snapshot = SavedTrip.fromContract(context(), TestTrips.raw(context(), TestTrips.NEW_YORK));
        assertEquals("demo_new_york_10d", snapshot.id);
        assertEquals(10, snapshot.totalDays);
        assertTrue(snapshot.route.contains("Sydney"));
        assertTrue(snapshot.route.contains("New York"));
        SavedTrip restored = SavedTrip.fromJson(snapshot.toJson().toString());
        assertEquals(snapshot.contractJson, restored.contractJson);
    }

    @Test public void excessiveNestingIsRejectedBeforeParsing() throws Exception {
        StringBuilder raw = new StringBuilder("{\"value\":");
        for (int i = 0; i < 3000; i++) raw.append('[');
        raw.append('0');
        for (int i = 0; i < 3000; i++) raw.append(']');
        raw.append('}');
        try { ItineraryDocument.parse(context(), raw.toString()); fail("Deep JSON accepted"); }
        catch (org.json.JSONException expected) { assertTrue(expected.getMessage().contains("nesting")); }
        try { SavedTrip.fromJson(raw.toString()); fail("Deep saved JSON accepted"); }
        catch (org.json.JSONException expected) { assertTrue(expected.getMessage().contains("nesting")); }
        ItineraryJson.preflight("{\"text\":\"[\\\"{\"}", 100);
        String bypass = "{a:'\"',b:" + raw.substring(9, raw.length() - 1) + ",c:'\"'}";
        try { ItineraryDocument.parse(context(), bypass); fail("Lenient quote bypass accepted"); }
        catch (org.json.JSONException expected) { assertTrue(expected.getMessage().contains("Non-standard")); }
    }

    private void reject(JSONObject value) {
        try { ItineraryDocument.parse(context(), value.toString()); fail("Malformed contract accepted"); }
        catch (org.json.JSONException expected) { /* Explicit validation failure, never silent defaults. */ }
    }

    @Test public void tenDayExampleRendersAndRestoresDaySelection() {
        Intent intent = TestTrips.saved(context(), TestTrips.NEW_YORK);
        try (ActivityScenario<TripDetailActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                View day = activity.findViewById(R.id.tripDetailContent).findViewWithTag("contract-day-8");
                assertNotNull("Ten-day JSON should expose Day 9", day);
                day.performClick();
                assertTrue(contains(activity.findViewById(R.id.tripDetailContent), "In transit"));
            });
            scenario.recreate();
            scenario.onActivity(activity -> assertTrue(contains(
                    activity.findViewById(R.id.tripDetailContent), "In transit")));
        }
    }

    static boolean contains(View view, String value) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(value)) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (contains(group.getChildAt(i), value)) return true;
        }
        return false;
    }
}
