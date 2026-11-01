package com.example.odyssey;

import static org.junit.Assert.*;
import android.content.Context;
import android.app.Application;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicBoolean;

/** Requires backend/tests/android_http_server.py on host port 8765. Synthetic facts only. */
public class LiveHttpDemoTest {
    private static final String URL = "http://10.0.2.2:8765";
    private interface Check { boolean ready(); }
    private void await(Check check) {
        long end = SystemClock.elapsedRealtime() + 15_000;
        AtomicBoolean ready = new AtomicBoolean();
        while (SystemClock.elapsedRealtime() < end) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> ready.set(check.ready()));
            if (ready.get()) return;
            SystemClock.sleep(50);
        }
        fail("Timed out waiting for live HTTP state");
    }
    @Test public void confirmsEightStepsGeneratesOnceAcrossRotationAndRecoversServerTrip() throws Exception {
        Application app = (Application) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        BackendConfig config = new BackendConfig(app);
        String original = config.url(), prefix = config.key(URL);
        java.util.Map<String, ?> previous = new java.util.HashMap<>(config.preferences.getAll());
        for (String key : previous.keySet()) if (key.startsWith(prefix)) config.preferences.edit().remove(key).commit();
        config.saveUrl(URL);
        java.lang.reflect.Field modelsField = LivePlannerModel.class.getDeclaredField("MODELS"); modelsField.setAccessible(true);
        @SuppressWarnings("unchecked") java.util.Map<String, LivePlannerModel> models = (java.util.Map<String, LivePlannerModel>) modelsField.get(null);
        LivePlannerModel previousModel = models.remove(URL);
        LivePlannerModel model = LivePlannerModel.get(app);
        String owner = config.owner(URL);
        BackendHttp http = new BackendHttp(URL, owner);
        JSONObject initialStats = http.call("GET", "/test/stats", null, false);
        int before = initialStats.getInt("generation"), beforeTurns = initialStats.getInt("turn");
        try (ActivityScenario<LivePlannerActivity> scenario = ActivityScenario.launch(LivePlannerActivity.class)) {
            scenario.onActivity(activity -> activity.findViewById(R.id.liveContent).findViewWithTag("live-start").performClick());
            await(() -> !model.busy && model.session != null);
            assertEquals(owner, new BackendConfig(app).owner(URL));
            String[] messages = {"simulate-lost-reply", "Destination Osaka, Japan", "Depart 2026-12-10, leave destination 2026-12-13",
                    "Exactly two adults and no children", "AUD 5000 whole party whole trip including flights", "No extra requirements", "Relaxed pace", "Parks and local food"};
            for (int i = 0; i < messages.length; i++) {
                final int index = i;
                scenario.onActivity(activity -> {
                    ((android.widget.EditText) activity.findViewById(R.id.liveInput)).setText(messages[index]);
                    activity.findViewById(R.id.liveSend).performClick();
                });
                if (i == 0) {
                    await(() -> !model.busy && model.pendingTurn != null);
                    JSONObject pending = new JSONObject(model.pendingTurn.toString());
                    scenario.recreate(); await(() -> !model.busy);
                    scenario.onActivity(activity -> model.replay());
                    await(() -> !model.busy && model.pendingTurn == null);
                    assertNotNull(pending.getString("requestId"));
                    assertEquals("AWAITING_CONFIRMATION", model.session.getJSONObject("steps").getJSONObject("ORIGIN").getString("state"));
                }
                await(() -> !model.busy && model.session.optJSONObject("proposal") != null);
                assertEquals("AWAITING_CONFIRMATION", model.session.getJSONObject("steps").getJSONObject(LivePlanningProtocol.STEPS[i]).getString("state"));
                assertFalse(model.session.getJSONObject("generationReadiness").getBoolean("ready"));
                if (i == 0) { scenario.recreate(); await(() -> !model.busy); }
                scenario.onActivity(activity -> activity.findViewById(R.id.liveContent).findViewWithTag("live-confirm-0").performClick());
                await(() -> !model.busy && model.session.optJSONObject("proposal") == null);
                assertEquals("CONFIRMED", model.session.getJSONObject("steps").getJSONObject(LivePlanningProtocol.STEPS[i]).getString("state"));
            }
            assertTrue(model.session.getJSONObject("generationReadiness").getBoolean("ready"));
            assertEquals(beforeTurns + 8, http.call("GET", "/test/stats", null, false).getInt("turn"));
            scenario.onActivity(activity -> model.generate());
            await(() -> model.busy && model.generationUncertain);
            scenario.recreate();
            await(() -> !model.busy && !model.savedTripId.isEmpty());
            assertFalse(model.generationUncertain);
            String id = model.savedTripId;
            SavedTrip saved = new TripStore(app).get(id);
            assertNotNull(saved);
            assertEquals("odyssey.frontend.itinerary", new JSONObject(saved.contractJson).getString("contractId"));
            assertEquals("MOCK", new JSONObject(saved.contractJson).getString("dataMode"));
            assertEquals(before + 1, http.call("GET", "/test/stats", null, false).getInt("generation"));
            scenario.onActivity(activity -> {
                assertNotNull(activity.findViewById(R.id.liveContent).findViewWithTag("live-open"));
                model.listTrips();
            });
            await(() -> !model.busy && model.serverTrips.length() > 0);
            scenario.onActivity(activity -> model.recover(id));
            await(() -> !model.busy);
            assertEquals(id, model.savedTripId);
            app.getSharedPreferences(TripStore.FILE, Context.MODE_PRIVATE).edit().remove("trip_" + id).commit();
            http.call("POST", "/test/lose-next-generation", new JSONObject(), false);
            scenario.onActivity(activity -> model.generate());
            await(() -> !model.busy && model.generationUncertain);
            assertTrue(model.resultJson.isEmpty());
            assertEquals(before + 2, http.call("GET", "/test/stats", null, false).getInt("generation"));
            LivePlannerModel interrupted = new LivePlannerModel(app);
            assertTrue(interrupted.generationUncertain);
            scenario.onActivity(activity -> model.listTrips());
            await(() -> !model.busy && model.serverTrips.length() >= 2);
            String recoveredId = model.serverTrips.getJSONObject(model.serverTrips.length() - 1).getString("id");
            scenario.onActivity(activity -> model.recover(recoveredId));
            await(() -> !model.busy && !model.generationUncertain);
            assertEquals(recoveredId, model.savedTripId);
            assertEquals(before + 2, http.call("GET", "/test/stats", null, false).getInt("generation"));
            app.getSharedPreferences(TripStore.FILE, Context.MODE_PRIVATE).edit().remove("trip_" + recoveredId).commit();
            LivePlannerModel restarted = new LivePlannerModel(app);
            assertEquals(model.session.getString("sessionId"), restarted.session.getString("sessionId"));
            assertEquals(recoveredId, restarted.savedTripId);
        } finally {
            config.saveUrl(original);
            android.content.SharedPreferences.Editor edit = config.preferences.edit();
            for (String key : config.preferences.getAll().keySet()) if (key.startsWith(prefix)) edit.remove(key);
            for (java.util.Map.Entry<String, ?> entry : previous.entrySet()) if (entry.getKey().startsWith(prefix)) edit.putString(entry.getKey(), (String) entry.getValue());
            edit.commit(); models.remove(URL); if (previousModel != null) models.put(URL, previousModel);
        }
    }
    @Test public void budgetReviewShowsStructuredAmountAndCurrencyPrecision() throws Exception {
        JSONObject answer = new JSONObject().put("description", "Budget description")
                .put("currency", "AUD").put("amountMinor", 500000)
                .put("scope", "ENTIRE_PARTY_WHOLE_TRIP").put("includesFlights", true);
        assertTrue(LivePlanningProtocol.answer("BUDGET", answer).contains("AUD 5000.00"));
        answer.put("currency", "JPY");
        assertTrue(LivePlanningProtocol.answer("BUDGET", answer).contains("JPY 500000"));
        answer.put("amountMinor", JSONObject.NULL);
        assertTrue(LivePlanningProtocol.answer("BUDGET", answer).contains("Amount not specified"));
    }
    @Test public void endpointAndConfirmationValidationRejectUnsafeOrStaleInputs() throws Exception {
        assertEquals("http://10.0.2.2:8000", BackendConfig.normalize(" http://10.0.2.2:8000/ ", true));
        for (String value : new String[]{"http://server", "https://user:secret@server", "https://server?key=secret", "file:///tmp/a"}) {
            try { BackendConfig.normalize(value, false); fail(value); } catch (IllegalArgumentException expected) { }
        }
        JSONObject session = new JSONObject().put("revision", 3).put("proposal", new JSONObject().put("id", "proposal")
                .put("stepId", "DATES").put("options", new org.json.JSONArray()));
        try { LivePlanningProtocol.confirm(session, "stale"); fail(); } catch (IllegalArgumentException expected) { }
    }
}
