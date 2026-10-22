package com.example.odyssey;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ProfileContractsTest {
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private JSONObject profile() throws JSONException {
        return new JSONObject().put("schema_version", 1).put("age", 25)
                .put("residential_location", "Sydney, Australia").put("travel_preferences", "");
    }
    private void reject(JSONObject value, String definition) throws JSONException {
        try {
            ProfileContracts.check(value, ProfileContracts.schema(context()), definition);
            fail("Invalid " + definition + " accepted: " + value);
        } catch (JSONException expected) { /* Reject before saving or consuming model output. */ }
    }
    @Test public void profileTypesRequiredFieldsAndLengthsAreStrict() throws Exception {
        JSONObject schema = ProfileContracts.schema(context());
        ProfileContracts.check(profile(), schema, "profile");
        reject(profile().put("age", "25"), "profile");
        reject(profile().put("age", 25.5), "profile");
        reject(profile().put("age", JSONObject.NULL), "profile");
        reject(profile().put("schema_version", "1"), "profile");
        reject(profile().put("extra", true), "profile");
        JSONObject missing = profile(); missing.remove("residential_location"); reject(missing, "profile");
        reject(profile().put("residential_location", " \n\t"), "profile");
        reject(profile().put("residential_location", repeat("a", 201)), "profile");
        reject(profile().put("travel_preferences", repeat("a", 2001)), "profile");
        ProfileContracts.check(profile().put("residential_location", "New York\nUSA"), schema, "profile");
        ProfileContracts.check(profile().put("travel_preferences", repeat("a", 2000)), schema, "profile");
    }
    @Test public void sharedStringChecksUseSchemaLimitsAndSearchPatterns() throws Exception {
        JSONObject schema = new JSONObject("{\"type\":\"string\",\"minLength\":1,\"maxLength\":2,\"pattern\":\"x\"}");
        ItineraryShape.check("ax", schema, schema, 0); // JSON Schema patterns search, not whole-string matches.
        try { ItineraryShape.check("xxx", schema, schema, 0); fail("maxLength ignored"); }
        catch (JSONException expected) { }
        JSONObject unicode = new JSONObject("{\"type\":\"string\",\"minLength\":1,\"maxLength\":1}");
        ItineraryShape.check("\uD83C\uDF0D", unicode, unicode, 0); // One Unicode code point, two UTF-16 chars.
        JSONObject anchored = new JSONObject("{\"type\":\"string\",\"pattern\":\"^[A-Z]{3}$\"}");
        try { ItineraryShape.check("xSYDx", anchored, anchored, 0); fail("Anchors ignored"); }
        catch (JSONException expected) { }
    }
    @Test public void recommendationsAcceptUnknownTripChoicesButRequireProfile() throws Exception {
        JSONObject original = profile();
        JSONObject request = ProfileContracts.recommendationRequest(context(), original, null, null);
        assertTrue(request.isNull("origin"));
        assertEquals(0, request.getJSONArray("destinations").length());
        assertEquals("Sydney, Australia", request.getJSONObject("profile").getString("residential_location"));
        request.getJSONObject("profile").put("age", 30);
        assertEquals(25, original.getInt("age"));
        JSONObject chosen = ProfileContracts.recommendationRequest(context(), original, " Melbourne ", new JSONArray().put("Kyoto"));
        assertEquals("Melbourne", chosen.getString("origin"));
        reject(chosen.put("origin", " "), "recommendationRequest");
        reject(request.put("profile", JSONObject.NULL), "recommendationRequest");
        reject(ProfileContracts.recommendationRequest(context(), original, null, null)
                .put("destinations", JSONObject.NULL), "recommendationRequest");
    }
    @Test public void modelResponseRequiresLocationsAndReasonsAndRejectsMalformedJson() throws Exception {
        JSONObject option = new JSONObject().put("location", "Sydney").put("reason", "Near your residential location.");
        JSONObject response = new JSONObject().put("schemaVersion", "1.0").put("departure", option)
                .put("destinations", new JSONArray().put(new JSONObject().put("location", "Kyoto")
                        .put("reason", "Museums and food match your interests.")));
        JSONObject parsed = ProfileContracts.recommendationResponse(context(), response.toString());
        assertEquals("Kyoto", parsed.getJSONArray("destinations").getJSONObject(0).getString("location"));
        reject(new JSONObject(response.toString()).put("destinations", new JSONArray()), "recommendationResponse");
        reject(new JSONObject(response.toString()).put("departure", JSONObject.NULL), "recommendationResponse");
        option.remove("reason"); reject(response, "recommendationResponse");
        try { ProfileContracts.recommendationResponse(context(), "{'schemaVersion':'1.0'}"); fail("Lenient JSON accepted"); }
        catch (JSONException expected) { assertTrue(expected.getMessage().contains("Non-standard")); }
    }
    private String repeat(String text, int count) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < count; i++) result.append(text);
        return result.toString();
    }
}
