package com.example.odyssey;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

/** Validated snapshot. JSON objects stay package-private and are treated as read-only. */
final class ItineraryDocument {
    final JSONObject root;
    private final Map<String, JSONObject> entities = new HashMap<>();
    private final Map<String, String> kinds = new HashMap<>();
    private ItineraryDocument(JSONObject root) { this.root = root; }

    static ItineraryDocument parse(Context context, String raw) throws JSONException {
        ItineraryJson.preflight(raw, ItineraryAssets.MAX_BYTES);
        JSONObject root = new JSONObject(raw);
        JSONObject schema = ItineraryAssets.schema(context);
        ItineraryShape.check(root, schema, schema, 0);
        ItineraryDocument doc = new ItineraryDocument(root);
        doc.index(new JSONArray().put(root), "itinerary");
        for (String kind : new String[]{"places", "media", "transportGroups", "stayGroups", "days"})
            doc.index(root.getJSONArray(kind), kind);
        doc.index(root.getJSONObject("budget").getJSONArray("lineItems"), "cost");
        doc.index(root.getJSONObject("money").getJSONArray("exchangeRates"), "exchange");
        doc.index(root.getJSONObject("quality").getJSONArray("sources"), "source");
        doc.index(root.getJSONObject("requestSnapshot").getJSONArray("travelers"), "traveler");
        doc.index(root.getJSONObject("requestSnapshot").getJSONArray("constraints"), "constraint");
        doc.index(root.getJSONArray("entryRequirements"), "entry");
        JSONArray days = root.getJSONArray("days");
        if (days.length() != root.getJSONObject("summary").getInt("dayCount")) ItineraryShape.fail("Day count mismatch");
        for (int i = 0; i < days.length(); i++) {
            JSONObject day = days.getJSONObject(i);
            if (day.getInt("dayNumber") != i + 1) ItineraryShape.fail("Day order");
            doc.index(day.getJSONArray("events"), "event");
        }
        for (String kind : new String[]{"transportGroups", "stayGroups"}) {
            JSONArray groups = root.getJSONArray(kind);
            for (int i = 0; i < groups.length(); i++) {
                JSONObject group = groups.getJSONObject(i);
                JSONArray options = group.getJSONArray("options");
                doc.index(options, "option");
                for (String key : new String[]{"recommendedOptionId", "planningOptionId", "userSelectedOptionId"}) {
                    if (group.isNull(key)) continue;
                    boolean found = false;
                    for (int j = 0; j < options.length(); j++)
                        found |= options.getJSONObject(j).getString("id").equals(group.getString(key));
                    if (!found) ItineraryShape.fail("Option outside group");
                }
                if (kind.equals("transportGroups")) for (int j = 0; j < options.length(); j++)
                    doc.index(options.getJSONObject(j).getJSONArray("legs"), "leg");
            }
        }
        doc.references(root);
        return doc;
    }
    private void index(JSONArray array, String kind) throws JSONException {
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            String id = item.getString("id");
            if (entities.put(id, item) != null) ItineraryShape.fail("Duplicate ID");
            kinds.put(id, kind);
        }
    }
    private void references(Object value) throws JSONException {
        if (value instanceof JSONArray) {
            JSONArray items = (JSONArray) value;
            for (int i = 0; i < items.length(); i++) references(items.get(i));
        } else if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object child = object.get(key);
                String kind = referenceKind(key);
                if (kind != null && child != JSONObject.NULL) {
                    if (child instanceof JSONArray) {
                        JSONArray ids = (JSONArray) child;
                        for (int i = 0; i < ids.length(); i++) require(ids.getString(i), kind);
                    } else require(object.getString(key), kind);
                }
                references(child);
            }
        }
    }
    private String referenceKind(String key) {
        if (key.equals("providerPlaceId")) return null;
        if (key.endsWith("PlaceId") || key.equals("placeId") || key.endsWith("PlaceIds")) return "places";
        if (key.equals("imageIds") || key.equals("coverImageId")) return "media";
        if (key.endsWith("CostItemIds") || key.equals("costItemIds")) return "cost";
        if (key.equals("transportGroupId")) return "transportGroups";
        if (key.equals("stayGroupId")) return "stayGroups";
        if (key.equals("continuingEventIds")) return "event";
        if (key.equals("sourceIds")) return "source";
        if (key.equals("travelerIds")) return "traveler";
        if (key.equals("constraintId")) return "constraint";
        if (key.equals("exchangeRateId")) return "exchange";
        if (key.equals("relatedIds")) return "any";
        return null;
    }
    private void require(String id, String kind) throws JSONException {
        if (!(kind.equals("any") ? kinds.containsKey(id) : kind.equals(kinds.get(id)))) ItineraryShape.fail("Broken " + kind + " reference");
    }
    JSONObject get(String id) throws JSONException {
        JSONObject value = entities.get(id);
        if (value == null) ItineraryShape.fail("Missing item");
        return value;
    }
    String place(String id) throws JSONException { return get(id).getString("name"); }
    JSONObject selected(JSONObject group) throws JSONException {
        // A recommendation is not necessarily part of the scheduled/priced plan.
        return group.isNull("planningOptionId") ? null : get(group.getString("planningOptionId"));
    }
}
