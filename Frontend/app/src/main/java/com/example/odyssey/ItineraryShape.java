package com.example.odyssey;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** Bounded shape checks for this bundled schema, NOT a general JSON Schema engine.
 * Calendar feasibility, evidence and budget arithmetic require backend validation. */
final class ItineraryShape {
    static void check(Object value, JSONObject schema, JSONObject root, int depth) throws JSONException {
        if (depth > 40) fail("Nesting limit");
        if (schema.has("$ref")) {
            String ref = schema.getString("$ref");
            if (!ref.startsWith("#/$defs/")) fail("Unsupported reference");
            check(value, root.getJSONObject("$defs").getJSONObject(ref.substring(8)), root, depth + 1);
            return;
        }
        for (String variant : new String[]{"anyOf", "oneOf"}) {
            if (!schema.has(variant)) continue;
            JSONArray choices = schema.getJSONArray(variant);
            int matches = 0;
            for (int i = 0; i < choices.length(); i++) {
                try { check(value, choices.getJSONObject(i), root, depth + 1); matches++; }
                catch (JSONException ignored) { /* Try the next explicitly allowed shape. */ }
            }
            if (matches == 0 || (variant.equals("oneOf") && matches != 1)) fail("Invalid variant");
            return;
        }
        if (schema.has("const") && !schema.get("const").equals(value)) fail("Unsupported constant");
        if (schema.has("enum")) {
            boolean found = false;
            JSONArray values = schema.getJSONArray("enum");
            for (int i = 0; i < values.length(); i++) found |= values.get(i).equals(value);
            if (!found) fail("Unknown enum");
        }
        String type = schema.optString("type");
        boolean valid = type.isEmpty() || (type.equals("null") && value == JSONObject.NULL)
                || (type.equals("object") && value instanceof JSONObject)
                || (type.equals("array") && value instanceof JSONArray)
                || (type.equals("string") && value instanceof String)
                || (type.equals("boolean") && value instanceof Boolean)
                || (type.equals("number") && value instanceof Number)
                || (type.equals("integer") && value instanceof Number
                    && ((Number) value).doubleValue() == Math.rint(((Number) value).doubleValue()));
        if (!valid) fail("Incorrect field type");
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            JSONArray required = schema.optJSONArray("required");
            if (required != null) for (int i = 0; i < required.length(); i++)
                if (!object.has(required.getString(i))) fail("Missing " + required.getString(i));
            JSONObject properties = schema.optJSONObject("properties");
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (properties != null && properties.has(key))
                    check(object.get(key), properties.getJSONObject(key), root, depth + 1);
                else if (!schema.optBoolean("additionalProperties", true)) fail("Unknown field " + key);
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            if (array.length() < schema.optInt("minItems", 0)
                    || array.length() > schema.optInt("maxItems", 10000)) fail("Array length");
            Set<String> unique = new HashSet<>();
            for (int i = 0; i < array.length(); i++) {
                if (schema.optBoolean("uniqueItems") && !unique.add(array.get(i).toString())) fail("Duplicate item");
                if (schema.has("items")) check(array.get(i), schema.getJSONObject("items"), root, depth + 1);
            }
        } else if (value instanceof String) {
            String text = (String) value;
            if (text.length() < schema.optInt("minLength", 0) || text.length() > 20000) fail("Text length");
            if (schema.has("pattern") && !text.matches(schema.getString("pattern"))) fail("Text format");
        } else if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (!Double.isFinite(number) || number < schema.optDouble("minimum", -Double.MAX_VALUE)
                    || number > schema.optDouble("maximum", Double.MAX_VALUE)) fail("Number range");
        }
    }
    static void fail(String message) throws JSONException { throw new JSONException(message); }
    private ItineraryShape() {}
}
