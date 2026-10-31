package com.example.odyssey;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;

/** 前端 v2 和旧版 v1 都能展示；后端另一种 v2 不能冒充前端协议。 */
public class FrontendItineraryV2Test {
    @Test public void acceptsBothVersionsAndVariableDayCounts() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        for (String name : new String[]{"canberra-3-days", "new-york-10-days"}) {
            ItineraryDocument old = ItineraryDocument.parse(context, ItineraryAssets.read(context, "examples/" + name + ".json"));
            ItineraryDocument latest = ItineraryDocument.parse(context, ItineraryAssets.read(context, "examples/" + name + "-frontend-v2.json"));
            assertEquals(old.root.getJSONArray("days").length(), latest.root.getJSONArray("days").length());
            assertEquals("odyssey.frontend.itinerary", latest.root.getString("contractId"));
        }
    }
    @Test public void rejectsUnidentifiedV2() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        JSONObject value = new JSONObject(ItineraryAssets.read(context, "examples/canberra-3-days-frontend-v2.json"));
        value.remove("contractId");
        try { ItineraryDocument.parse(context, value.toString()); fail("Missing contract identifier"); }
        catch (org.json.JSONException | IllegalArgumentException expected) { }
    }
    @Test public void acceptsActualBackendAssemblyAndUnknownFacts() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String raw = ItineraryAssets.read(context, "examples/backend-generated-frontend-v2.json");
        ItineraryDocument doc = ItineraryDocument.parse(context, raw);
        assertEquals("LIVE", doc.root.getString("dataMode"));
        assertEquals(5, doc.root.getJSONArray("days").length());
        assertTrue(doc.root.getJSONArray("stayGroups").getJSONObject(0)
                .getJSONArray("options").getJSONObject(0).isNull("guestCapacity"));
        SavedTrip snapshot = SavedTrip.fromContract(context, raw);
        assertEquals("backend_contract_fixture", snapshot.id);
        assertEquals(5, SavedTrip.fromJson(snapshot.toJson().toString()).totalDays);
    }
    @Test public void rendersActualBackendOutputOnEveryDay() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String raw = ItineraryAssets.read(context, "examples/backend-generated-frontend-v2.json");
        try (androidx.test.core.app.ActivityScenario<HomeActivity> scenario =
                     androidx.test.core.app.ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    ItineraryDocument doc = ItineraryDocument.parse(activity, raw);
                    for (int day = 0; day < doc.root.getJSONArray("days").length(); day++) {
                        android.widget.LinearLayout content = new android.widget.LinearLayout(activity);
                        content.setOrientation(android.widget.LinearLayout.VERTICAL);
                        new ItineraryRenderer(new HomeUi(content), doc, day, ignored -> { }).render();
                        assertTrue(ItineraryIntegrationTest.contains(content, "Estimated budget"));
                        assertTrue(ItineraryIntegrationTest.contains(content, "Where to stay"));
                    }
                } catch (org.json.JSONException error) {
                    throw new AssertionError("Backend output failed rendering", error);
                }
            });
        }
    }
}
