package com.example.odyssey;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class ProfileStoreTest {
    @Test public void completeProfileRoundtripAndRejectedEditPreservesPrevious() throws Exception {
        File path = fixture();
        try {
            ProfileStore store = store(path);
            store.save(30, "Sydney", "Art");
            assertFalse(ProfileStore.isComplete(store.load()));
            JSONObject profile = new JSONObject().put("schema_version", 2).put("name", " Alex ")
                    .put("gender", "Prefer not to say").put("age", 30).put("nationality", "Australian")
                    .put("residential_location", "Sydney").put("travel_preferences", "Art")
                    .put("preference_tags", new org.json.JSONArray().put("DIVING"));
            store.save(profile);
            assertTrue(ProfileStore.isComplete(store(path).load()));
            assertEquals("Alex", store.load().getString("name"));
            profile.put("name", " ");
            assertThrows(IllegalArgumentException.class, () -> store.save(profile));
            assertEquals("Alex", store.load().getString("name"));
        } finally { path.delete(); }
    }
    @Test public void profileSurvivesReopeningAndPreferencesAreOptional() throws Exception {
        File path = fixture();
        try {
            ProfileStore store = store(path);
            assertNull(store.load());
            store.save(25, " Sydney, Australia ", "");
            JSONObject profile = store(path).load();
            assertEquals(1, profile.getInt("schema_version"));
            assertEquals(25, profile.getInt("age"));
            assertEquals("Sydney, Australia", profile.getString("residential_location"));
            assertEquals("", profile.getString("travel_preferences"));
            store.save(26, "Melbourne", "Food, museums\nQuiet walks");
            assertEquals("Food, museums\nQuiet walks", store.load().getString("travel_preferences"));
        } finally { path.delete(); }
    }
    @Test public void invalidProfileDoesNotReplaceSavedData() throws Exception {
        File path = fixture();
        try {
            ProfileStore store = store(path);
            store.save(30, "Sydney", "Art");
            for (int age : new int[]{0, -1, 121}) {
                try { store.save(age, "Sydney", ""); fail("Invalid age accepted"); }
                catch (JSONException expected) { assertEquals(30, store.load().getInt("age")); }
            }
            try { store.save(30, "  ", ""); fail("Blank location accepted"); }
            catch (JSONException expected) { assertEquals("Sydney", store.load().getString("residential_location")); }
        } finally { path.delete(); }
    }
    @Test public void corruptFileIsReportedAndCanBeReplaced() throws Exception {
        File path = fixture();
        try {
            Files.write(path.toPath(), "not-json".getBytes(StandardCharsets.UTF_8));
            ProfileStore store = store(path);
            try { store.load(); fail("Corrupt data accepted"); }
            catch (JSONException expected) {
                assertEquals("not-json", new String(Files.readAllBytes(path.toPath()), StandardCharsets.UTF_8));
            }
            store.save(30, "Sydney", "");
            assertNotNull(store.load());
        } finally { path.delete(); }
    }
    @Test public void malformedStoredTypesAreRejectedWithoutChangingFile() throws Exception {
        File path = fixture();
        try {
            String raw = "{\"schema_version\":1,\"age\":\"30\",\"residential_location\":\"Sydney\",\"travel_preferences\":\"\"}";
            Files.write(path.toPath(), raw.getBytes(StandardCharsets.UTF_8));
            try { store(path).load(); fail("Coerced age accepted"); }
            catch (JSONException expected) {
                assertEquals(raw, new String(Files.readAllBytes(path.toPath()), StandardCharsets.UTF_8));
            }
        } finally { path.delete(); }
    }

    private ProfileStore store(File path) throws JSONException {
        return new ProfileStore(path, ProfileContracts.schema(
                InstrumentationRegistry.getInstrumentation().getTargetContext()));
    }
    private File fixture() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return new File(context.getCacheDir(), "profile-test-" + UUID.randomUUID() + ".json");
    }
}
