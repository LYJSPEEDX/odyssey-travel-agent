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

    @Test public void generatedSnapshotContainsStructuredContract() throws Exception {
        TripPlanState state = new TripPlanState();
        state.selectDeparture("Sydney"); state.selectDestination("Kyoto");
        state.selectDate(20261110); state.selectDate(20261119);
        state.setCompanion("Couple"); state.setBudget("Comfort");
        state.setNote(5, "Avoid stairs");
        for (int i = 0; i < 5; i++) state.goNext();
        state.finishGeneration();
        JSONObject saved = SavedTrip.fromPlan(context(), state, "test_contract").toJson();
        assertEquals(2, saved.getInt("schema"));
        JSONObject contract = saved.getJSONObject("contract");
        assertEquals("1.0", contract.getString("schemaVersion"));
        assertEquals(10, contract.getJSONArray("days").length());
        assertTrue(contract.getJSONObject("requestSnapshot").getJSONArray("notes").toString().contains("Avoid stairs"));
        assertEquals(saved.toString(), SavedTrip.fromJson(saved.toString()).toJson().toString());
    }

    @Test public void legacySnapshotsRemainReadable() throws Exception {
        SavedTrip old = new SavedTrip("legacy", 1, "Kyoto", "Sydney → Kyoto", "Flexible", 3, "Brief", "Old result");
        SavedTrip read = SavedTrip.fromJson(old.toJson().toString());
        assertEquals("Old result", read.itinerary);
    }

    @Test public void examplesRenderEveryDayAndRejectBrokenContracts() throws Exception {
        for (String id : new String[]{"contract_canberra", "contract_new_york"}) {
            String raw = ItineraryAssets.example(context(), id);
            ItineraryDocument document = ItineraryDocument.parse(context(), raw);
            int days = document.root.getJSONArray("days").length();
            Intent intent = new Intent(context(), TripDetailActivity.class)
                    .putExtra(TripDetailActivity.EXTRA_SAMPLE, true).putExtra(TripDetailActivity.EXTRA_ID, id);
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
        ItineraryDocument doc = ItineraryDocument.parse(context(), ItineraryAssets.example(context(), "contract_canberra"));
        JSONObject group = doc.root.getJSONArray("transportGroups").getJSONObject(0);
        String planned = group.getString("planningOptionId");
        group.put("userSelectedOptionId", JSONObject.NULL);
        assertEquals(planned, doc.selected(group).getString("id"));
        group.put("planningOptionId", JSONObject.NULL);
        assertNull(doc.selected(group));
    }

    private void reject(JSONObject value) {
        try { ItineraryDocument.parse(context(), value.toString()); fail("Malformed contract accepted"); }
        catch (org.json.JSONException expected) { /* Explicit validation failure, never silent defaults. */ }
    }

    @Test public void tenDayExampleRendersAndRestoresDaySelection() {
        Intent intent = new Intent(context(), TripDetailActivity.class)
                .putExtra(TripDetailActivity.EXTRA_SAMPLE, true)
                .putExtra(TripDetailActivity.EXTRA_ID, "contract_new_york");
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
