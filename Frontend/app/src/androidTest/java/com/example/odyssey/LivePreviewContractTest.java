package com.example.odyssey;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LivePreviewContractTest {
    @Test public void backendAssemblyParsesSavesAndRendersEveryDay() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String raw;
        String liveFile = InstrumentationRegistry.getArguments().getString("livePreviewFile", "");
        try (java.io.InputStream in = liveFile.isEmpty()
                ? InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("backend-preview.json")
                : new java.io.FileInputStream(new java.io.File(target.getFilesDir(), liveFile))) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            raw = out.toString("UTF-8");
        }
        SavedTrip saved = SavedTrip.fromContract(target, raw);
        JSONObject document = new JSONObject(saved.contractJson);
        assertEquals("LIVE", document.getString("dataMode"));
        assertEquals(document.getJSONObject("summary").getInt("dayCount"), saved.totalDays);
        assertEquals(1, document.getJSONArray("transportGroups").getJSONObject(0).getJSONArray("options").length());
        ItineraryDocument parsed = ItineraryDocument.parse(target, raw);
        assertTrue(parsed.selected(document.getJSONArray("stayGroups").getJSONObject(0)).isNull("guestCapacity"));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            android.widget.LinearLayout content = new android.widget.LinearLayout(new android.view.ContextThemeWrapper(target, R.style.Theme_Odyssey));
            content.setOrientation(android.widget.LinearLayout.VERTICAL);
            try {
                for (int day = 0; day < saved.totalDays; day++) {
                    content.removeAllViews(); new ItineraryRenderer(new HomeUi(content), parsed, day, index -> {}).render();
                }
            }
            catch (Exception e) { throw new AssertionError(e); }
        });
        assertEquals(saved.id, SavedTrip.fromJson(saved.toJson().toString()).id);
    }

    @Test public void requestPreservesFactsAndRejectsMissingPartyOrFlexibleDates() throws Exception {
        TripPlanState state = new TripPlanState();
        state.selectDeparture("Sydney"); state.selectDestination("Osaka");
        state.selectDate(20261210); state.selectDate(20261214);
        state.setCompanion("Friends"); state.setBudget("AUD 5000 whole trip"); state.setNote(5, "Slow pace");
        JSONObject request = TripPreviewClient.request(state, 3, "aud", "Food and parks");
        assertEquals(3, request.getInt("adults")); assertEquals("AUD", request.getString("currency"));
        assertEquals("2026-12-10", request.getString("startDate")); assertFalse(request.has("flexibleDates"));
        assertEquals("Food and parks", request.getJSONObject("notes").getString("1"));
        try { TripPreviewClient.request(state, 0, "AUD", ""); fail(); } catch (IllegalArgumentException expected) { }
        state.useFlexibleDates();
        try { TripPreviewClient.request(state, 3, "AUD", ""); fail(); } catch (IllegalArgumentException expected) { }
    }
}
