package com.example.odyssey;

import android.content.Context;
import org.json.JSONException;
import org.json.JSONObject;

/** Read-only snapshot: reopening a trip never regenerates or changes its saved content. */
final class SavedTrip {
    final String id;
    final long createdAt;
    final String destination;
    final String route;
    final String dates;
    final int totalDays;
    final String brief;
    final String itinerary;
    final String contractJson;

    SavedTrip(String id, long createdAt, String destination, String route, String dates,
              int totalDays, String brief, String itinerary) {
        this(id, createdAt, destination, route, dates, totalDays, brief, itinerary, null);
    }

    SavedTrip(String id, long createdAt, String destination, String route, String dates,
              int totalDays, String brief, String itinerary, String contractJson) {
        this.id = id;
        this.createdAt = createdAt;
        this.destination = destination;
        this.route = route;
        this.dates = dates;
        this.totalDays = totalDays;
        this.brief = brief;
        this.itinerary = itinerary;
        this.contractJson = contractJson;
    }

    static SavedTrip fromPlan(Context context, TripPlanState state, String id) {
        if (!state.isGenerated()) throw new IllegalStateException("Only completed results can be saved");
        String dates = state.isFlexibleDates() ? context.getString(R.string.flexible_dates_summary)
                : context.getString(R.string.date_range, TripDates.format(state.getStartDate()),
                TripDates.format(state.getEndDate()));
        String budget = TripOptions.budgetDescription(state);
        StringBuilder brief = new StringBuilder(context.getString(R.string.summary_companion, state.getCompanion()))
                .append("\n").append(context.getString(R.string.summary_budget, budget))
                .append("\n").append(context.getString(budget.equals(state.getBudget())
                        ? R.string.custom_budget_disclaimer : R.string.budget_disclaimer))
                .append("\n").append(context.getString(R.string.summary_constraints,
                        state.getConstraints().isEmpty() ? context.getString(R.string.no_restrictions)
                                : String.join(" · ", state.getConstraints())));
        String[] steps = context.getResources().getStringArray(R.array.step_names);
        for (int step = 1; step <= 5; step++) {
            if (!state.getNote(step).isEmpty()) brief.append("\n").append(
                    context.getString(R.string.summary_note, steps[step - 1], state.getNote(step)));
        }
        String contract;
        try { contract = MockItinerary.fromPlan(context, state, id).toString(); }
        catch (JSONException error) { throw new IllegalArgumentException("Cannot create itinerary", error); }
        return new SavedTrip(id, System.currentTimeMillis(), state.getDestination(),
                context.getString(R.string.trip_route, state.getDeparture(), state.getDestination()),
                dates, state.isFlexibleDates() ? 3 : state.getDurationDays(),
                brief.toString(), "", contract);
    }

    JSONObject toJson() throws JSONException {
        JSONObject result = new JSONObject().put("schema", contractJson == null ? 1 : 2).put("id", id).put("createdAt", createdAt)
                .put("destination", destination).put("route", route).put("dates", dates)
                .put("totalDays", totalDays).put("brief", brief).put("itinerary", itinerary);
        if (contractJson != null) result.put("contract", new JSONObject(contractJson));
        return result;
    }

    static SavedTrip fromJson(String value) throws JSONException {
        ItineraryJson.preflight(value, ItineraryAssets.MAX_BYTES * 2);
        JSONObject object = new JSONObject(value);
        int version = object.getInt("schema");
        if (version != 1 && version != 2) throw new JSONException("Unsupported saved trip schema");
        String contract = version == 2 ? object.getJSONObject("contract").toString() : null;
        if (contract != null && !object.getString("id").equals(object.getJSONObject("contract").getString("id")))
            throw new JSONException("Snapshot ID mismatch");
        return new SavedTrip(object.getString("id"), object.getLong("createdAt"),
                object.getString("destination"), object.getString("route"), object.getString("dates"),
                object.getInt("totalDays"), object.getString("brief"), object.getString("itinerary"), contract);
    }
}
