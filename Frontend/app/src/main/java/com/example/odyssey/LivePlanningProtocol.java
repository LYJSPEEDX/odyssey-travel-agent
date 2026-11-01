package com.example.odyssey;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

/** The backend protocol is independent from the offline MockPlanningService protocol. */
final class LivePlanningProtocol {
    static final String[] STEPS = PlanningSession.IDS;
    static final String[] TITLES = {"Departure", "Destination", "Dates", "Travellers", "Budget", "Requirements", "Pace", "Experiences"};
    static int index(String step) {
        for (int i = 0; i < STEPS.length; i++) if (STEPS[i].equals(step)) return i;
        throw new IllegalArgumentException("Unsupported planning step.");
    }
    static JSONObject turn(JSONObject session, String step, String text) throws JSONException {
        return new JSONObject().put("requestId", UUID.randomUUID().toString())
                .put("baseRevision", session.getInt("revision")).put("stepId", step)
                .put("action", "CHAT").put("text", InputValidation.requiredText(text, 500, "Message"));
    }
    static JSONObject confirm(JSONObject session, String optionId) throws JSONException {
        JSONObject proposal = session.getJSONObject("proposal");
        boolean found = false;
        JSONArray options = proposal.getJSONArray("options");
        for (int i = 0; i < options.length(); i++) found |= optionId.equals(options.getJSONObject(i).getString("id"));
        if (!found) throw new IllegalArgumentException("This option is no longer current. Refresh the session.");
        return new JSONObject().put("requestId", UUID.randomUUID().toString())
                .put("baseRevision", session.getInt("revision")).put("stepId", proposal.getString("stepId"))
                .put("action", "CONFIRM").put("proposalId", proposal.getString("id")).put("optionId", optionId);
    }
    static void validate(JSONObject response) throws JSONException {
        if (!"1.0".equals(response.getString("schemaVersion"))) throw new JSONException("Unsupported planning API version.");
        UUID.fromString(response.getString("sessionId"));
        if (response.getInt("revision") < 0) throw new JSONException("Invalid session revision.");
        index(response.getString("currentStep"));
        JSONObject steps = response.getJSONObject("steps");
        for (String step : STEPS) steps.getJSONObject(step).getString("state");
        response.getJSONObject("generationReadiness").getBoolean("ready");
    }
    static String answer(String step, JSONObject a) throws JSONException {
        if (a == null) return "Not confirmed yet";
        switch (step) {
            case "ORIGIN": case "DESTINATION": return a.getString("label") + " · " + a.getString("countryCode");
            case "DATES": return "FIXED".equals(a.getString("mode"))
                    ? "Depart " + a.getString("startDate") + " · Leave destination " + a.getString("endDate") : "Flexible dates";
            case "COMPANIONS": return a.getInt("adults") + " adults · " + (a.getJSONArray("childrenAges").length() == 0 ? "No children" : "Children aged " + a.getJSONArray("childrenAges")) + "\n" + a.getString("description");
            case "BUDGET": return a.getString("description") + "\n" + budgetAmount(a) + "\n" + a.getString("currency") + " · "
                    + a.getString("scope").toLowerCase(java.util.Locale.ROOT).replace('_', ' ')
                    + "\nFlights " + (a.getBoolean("includesFlights") ? "included" : "excluded");
            case "CONSTRAINTS":
                JSONArray requirements = a.getJSONArray("requirements"); StringBuilder result = new StringBuilder();
                for (int i = 0; i < requirements.length(); i++) { JSONObject r = requirements.getJSONObject(i);
                    if (i > 0) result.append("\n"); result.append(r.getString("description")).append(" · ").append(r.getString("strength")); }
                return result.length() == 0 ? "No extra requirements" : result.toString();
            case "FOLLOW_UP_1": return a.getString("description");
            default:
                JSONArray preferences = a.getJSONArray("preferences"); StringBuilder lines = new StringBuilder();
                for (int i = 0; i < preferences.length(); i++) { if (i > 0) lines.append(" · "); lines.append(preferences.getString(i)); }
                return lines.toString();
        }
    }
    private static String budgetAmount(JSONObject answer) throws JSONException {
        if (answer.isNull("amountMinor")) return "Amount not specified";
        String currency = answer.getString("currency"); long amount = answer.getLong("amountMinor");
        try {
            int digits = java.util.Currency.getInstance(currency).getDefaultFractionDigits();
            if (digits < 0) throw new IllegalArgumentException();
            return "Amount: " + currency + " " + java.math.BigDecimal.valueOf(amount).movePointLeft(digits).toPlainString();
        } catch (IllegalArgumentException unknown) {
            return "Amount: " + amount + " minor units of " + currency + " (currency precision unavailable)";
        }
    }
    private LivePlanningProtocol() { }
}
