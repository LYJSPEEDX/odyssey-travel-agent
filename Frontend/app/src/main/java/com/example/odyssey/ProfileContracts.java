package com.example.odyssey;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Profile and early recommendation contracts share the existing structural validator. */
final class ProfileContracts {
    static final int MAX_JSON_CHARS = 100_000;
    static JSONObject schema(Context context) throws JSONException {
        return new JSONObject(ItineraryAssets.read(context, "profile.schema.json"));
    }
    static void check(JSONObject value, JSONObject schema, String definition) throws JSONException {
        ItineraryShape.check(value, new JSONObject().put("$ref", "#/$defs/" + definition), schema, 0);
    }
    static JSONObject parse(String raw, JSONObject schema, String definition) throws JSONException {
        ItineraryJson.preflight(raw, MAX_JSON_CHARS);
        JSONObject value = new JSONObject(raw);
        check(value, schema, definition);
        return value;
    }
    /** Unknown origin is null; no destination choices is []. Neither confirms the home location as origin. */
    static JSONObject recommendationRequest(Context context, JSONObject profile, String origin,
                                             JSONArray destinations) throws JSONException {
        JSONObject request = new JSONObject().put("schemaVersion", "1.0")
                .put("profile", new JSONObject(profile.toString()))
                .put("origin", origin == null ? JSONObject.NULL : origin.trim())
                .put("destinations", destinations == null ? new JSONArray() : new JSONArray(destinations.toString()));
        check(request, schema(context), "recommendationRequest");
        return request;
    }
    /** Validate model output before consuming it; a recommendation is not a confirmed trip choice. */
    static JSONObject recommendationResponse(Context context, String raw) throws JSONException {
        return parse(raw, schema(context), "recommendationResponse");
    }
    private ProfileContracts() {}
}
