package com.example.odyssey;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

/** UID 生命周期、旧资料迁移和未来会话快照均使用独立临时文件测试。 */
public class ProfileIdentityTest {
    private ProfileStore store(File path) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return new ProfileStore(path, ProfileContracts.schema(context));
    }
    private File fixture() { return new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(), "identity-" + UUID.randomUUID() + ".json"); }
    private JSONObject profile() throws Exception {
        return new JSONObject().put("schema_version", 3).put("onboarding_complete", true)
                .put("name", "Alex").put("gender", "").put("birth_date", "2000-02-29")
                .put("nationality", "Australia").put("residential_location", "New South Wales, Australia")
                .put("residence_country_code", "AU").put("currency_override", "")
                .put("travel_preferences", "").put("preference_tags", new JSONArray().put("DIVING"));
    }
    @Test public void registrationCreatesUidAndEditsCannotReplaceIt() throws Exception {
        File path = fixture();
        try {
            ProfileStore store = store(path); JSONObject data = profile(); store.save(data);
            String uid = store.load().getString("uid"); assertEquals(uid, UUID.fromString(uid).toString());
            data.put("name", "Updated").put("uid", UUID.randomUUID().toString()); store.save(data);
            assertEquals(uid, store(path).load().getString("uid"));
            assertEquals("Updated", store.load().getString("name"));
            JSONObject payload = ProfilePayload.createSession(store.load()); JSONObject body = payload.getJSONObject("profile");
            assertEquals(uid, body.getString("uid")); assertEquals("AUD", body.getString("displayCurrency"));
            assertEquals("RESIDENCE", body.getString("currencySource"));
            assertEquals("2000-02-29", body.getString("birthDate")); assertTrue(body.isNull("gender"));
            assertEquals("DIVING", body.getJSONArray("travelTagIds").getString(0));
            assertFalse(body.has("latitude")); assertFalse(body.has("longitude"));
            data.put("name", "Later"); assertEquals("Updated", body.getString("displayName"));
        } finally { path.delete(); }
    }
    @Test public void oldCompletedProfileIsMigratedOnceWithoutLosingOtherFields() throws Exception {
        File path = fixture();
        try {
            JSONObject old = new JSONObject().put("schema_version", 2).put("name", "Alex")
                    .put("gender", "Prefer not to say").put("age", 30).put("nationality", "Australian")
                    .put("residential_location", "Sydney").put("travel_preferences", "Food")
                    .put("preference_tags", new JSONArray().put("FOOD")).put("unknown_legacy_data", "keep");
            Files.write(path.toPath(), old.toString().getBytes(StandardCharsets.UTF_8));
            ProfileStore store = store(path);
            String uid = store.load().getString("uid");
            assertEquals(uid, store(path).load().getString("uid"));
            JSONObject raw = new JSONObject(new String(Files.readAllBytes(path.toPath()), StandardCharsets.UTF_8));
            assertEquals("keep", raw.getString("unknown_legacy_data"));
            JSONObject payload = ProfilePayload.createSession(store.load()).getJSONObject("profile");
            assertEquals(30, payload.getInt("legacyAgeYears")); assertTrue(payload.isNull("birthDate"));
            assertTrue(payload.isNull("residenceCountryCode")); assertEquals("USD", payload.getString("displayCurrency"));
        } finally { path.delete(); }
    }
    @Test public void mockSessionRetainsProfileAndRejectsSilentChanges() throws Exception {
        File path = fixture();
        try {
            ProfileStore store = store(path); store.save(profile());
            String snapshot = ProfilePayload.createSession(store.load()).toString();
            PlanningSession session = new PlanningSession("Sydney", "Food"); session.profileRequest = snapshot;
            JSONObject request = PlanningJson.request(session, "REFINE", PlanningJson.textInput("Perth"));
            String response = new MockPlanningService().respond(request.toString(), PlanningJson.snapshot(session).toString());
            PlanningJson.Applied applied = PlanningJson.applyResult(session, request, response);
            assertEquals(store.load().getString("uid"), new JSONObject(applied.session.profileRequest)
                    .getJSONObject("profile").getString("uid"));
            JSONObject tampered = new JSONObject(response);
            tampered.getJSONObject("session").getJSONObject("profileContext").getJSONObject("registrationProfile")
                    .getJSONObject("profile").put("displayName", "Changed");
            assertThrows(IllegalArgumentException.class, () -> PlanningJson.applyResult(session, request, tampered.toString()));
        } finally { path.delete(); }
    }
    @Test public void invalidExistingUidIsNotSilentlyReplaced() throws Exception {
        File path = fixture();
        try {
            String raw = profile().put("uid", "broken").toString();
            Files.write(path.toPath(), raw.getBytes(StandardCharsets.UTF_8));
            assertThrows(org.json.JSONException.class, () -> store(path).load());
            assertEquals(raw, new String(Files.readAllBytes(path.toPath()), StandardCharsets.UTF_8));
        } finally { path.delete(); }
    }
    @Test public void concurrentReadsGenerateOneUid() throws Exception {
        File path = fixture();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            Files.write(path.toPath(), profile().toString().getBytes(StandardCharsets.UTF_8));
            java.util.List<java.util.concurrent.Future<String>> tasks = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) tasks.add(pool.submit(() -> store(path).load().getString("uid")));
            String expected = tasks.get(0).get();
            for (java.util.concurrent.Future<String> task : tasks) assertEquals(expected, task.get());
        } finally { pool.shutdownNow(); path.delete(); }
    }
}
