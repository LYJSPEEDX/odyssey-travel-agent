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
public class LiveHttpTest {
    // 模拟器连不到 10.0.2.2 时，可用 adb reverse 加 -e liveUrl http://127.0.0.1:8765。
    private static final String URL = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("liveUrl", "http://10.0.2.2:8765");
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
        InstrumentationRegistry.getInstrumentation().getUiAutomation().grantRuntimePermission(app.getPackageName(), "android.permission.ACCESS_LOCAL_NETWORK");
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
            // 打开页面即自动连接，不需要手动开始。
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
                scenario.onActivity(activity -> activity.findViewById(R.id.liveContent).findViewWithTag("live-confirm").performClick());
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
    @Test public void budgetChoicesFollowProfileCurrency() throws Exception {
        JSONObject creation = new JSONObject().put("profile", new JSONObject().put("displayCurrency", "GBP"));
        assertEquals("GBP 65–110 · per person, per day", LivePlanningProtocol.budgetTiers(creation)[0][1]);
        assertTrue(LivePlanningProtocol.budgetTiers(creation)[1][2].contains("GBP 140"));
        creation.getJSONObject("profile").put("displayCurrency", "XXX");
        assertTrue(LivePlanningProtocol.budgetTiers(creation)[0][1].startsWith("USD"));
        assertTrue(LivePlanningProtocol.budgetTiers(null)[0][1].startsWith("USD"));
    }
    @Test public void cityPhotosRejectWrongPlacesAndNonPhotos() throws Exception {
        String photo = "https://thumb.wikimedia.org/wikipedia/commons/thumb/a/ab/Macau.jpg/330px-Macau.jpg?utm_source=x";
        JSONObject macau = new JSONObject().put("type", "standard").put("description", "Special administrative region of China").put("extract", "Macau is in China.")
                .put("thumbnail", new JSONObject().put("source", photo));
        assertTrue(CityPhotos.matches(macau, "Macao"));
        assertTrue(CityPhotos.matches(macau, "Macau, China"));
        assertFalse(CityPhotos.matches(macau, "Macao, Taiwan"));
        assertFalse(CityPhotos.matches(new JSONObject().put("type", "disambiguation"), "Springfield"));
        assertTrue(CityPhotos.pick(macau).contains("/960px-"));
        assertEquals("none", CityPhotos.pick(new JSONObject().put("thumbnail", new JSONObject().put("source", photo.replace("Macau", "Flag_of_Macau")))));
        JSONObject media = new JSONObject("{items:[{type:'image',srcset:[{src:'//upload.wikimedia.org/x/Flag_of_Macau.svg/330px-Flag_of_Macau.svg.png'}]},"
                + "{type:'video',srcset:[{src:'//upload.wikimedia.org/x/a.jpg'}]},{type:'image',srcset:[{src:'//upload.wikimedia.org/x/Ruins.jpg/330px-Ruins.jpg'}]}]}");
        assertEquals("https://upload.wikimedia.org/x/Ruins.jpg/960px-Ruins.jpg", CityPhotos.pickFromMedia(media));
        JSONObject scenic = new JSONObject("{items:[{type:'image',srcset:[{src:'//upload.wikimedia.org/x/Old.tif/lossy-page1-500px-Old.tif.jpg'}]},"
                + "{type:'image',srcset:[{src:'//upload.wikimedia.org/x/Council.jpg/330px-Council.jpg'}]},{type:'image',srcset:[{src:'//upload.wikimedia.org/x/Aerial_view.jpg/330px-Aerial_view.jpg'}]}]}");
        assertEquals("https://upload.wikimedia.org/x/Aerial_view.jpg/960px-Aerial_view.jpg", CityPhotos.pickFromMedia(scenic));
        assertEquals(R.drawable.photo_tokyo, TripPhotos.resource("Tokyo, Japan"));
        assertEquals(R.drawable.bg_photo_placeholder, TripPhotos.resource("Bali, West Java, Indonesia"));
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
