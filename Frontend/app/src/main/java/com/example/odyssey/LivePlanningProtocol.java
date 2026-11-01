package com.example.odyssey;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

/** Requests, validation and display text for the backend planning API. */
final class LivePlanningProtocol {
    static final String[] STEPS = {"ORIGIN", "DESTINATION", "DATES", "COMPANIONS", "BUDGET", "CONSTRAINTS", "FOLLOW_UP_1", "FOLLOW_UP_2"};
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
            case "COMPANIONS": return a.getInt("adults") + (a.getInt("adults") == 1 ? " adult · " : " adults · ") + (a.getJSONArray("childrenAges").length() == 0 ? "No children" : "Children aged " + a.getJSONArray("childrenAges")) + "\n" + a.getString("description");
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
    /** 后端行程生成最多支持的成人数，超过时研究阶段会直接失败。 */
    static final int MAX_ADULTS = 9;
    static final String ADULT_LIMIT = "Odyssey can plan for up to " + MAX_ADULTS + " adults per trip.";
    static boolean tooManyAdults(JSONObject companions) { return companions != null && companions.optInt("adults") > MAX_ADULTS; }
    /** 选项和已确认答案的主文字：最重要的事实，用大字加粗。 */
    static String headline(String step, JSONObject a) throws JSONException {
        switch (step) {
            case "ORIGIN": case "DESTINATION": return a.getString("label");
            case "DATES": return "FIXED".equals(a.getString("mode")) ? dates(a) : "Flexible dates";
            case "COMPANIONS": return companions(a);
            case "BUDGET": return budgetTitle(a);
            default: return answer(step, a);
        }
    }
    /** 主文字之外的补充信息，没有时返回空字符串。 */
    static String detail(String step, JSONObject a) throws JSONException {
        switch (step) {
            case "DATES":
                if (!"FIXED".equals(a.getString("mode"))) return "";
                int nights = TripDates.daysBetweenInclusive(dateKey(a.getString("startDate")), dateKey(a.getString("endDate"))) - 1;
                return nights + (nights == 1 ? " night" : " nights");
            case "COMPANIONS": return a.getString("description");
            case "BUDGET": return budgetCaption(a);
            default: return "";
        }
    }
    /** 没有后端推荐接口可用，按资料里的所在地和国家给出发地建议。 */
    static java.util.List<String> originSuggestions(JSONObject creation) {
        JSONObject profile = creation == null ? null : creation.optJSONObject("profile");
        String home = profile == null || profile.isNull("residentialLocation") ? "" : profile.optString("residentialLocation");
        String country = profile == null || profile.isNull("residenceCountryCode") ? "" : profile.optString("residenceCountryCode");
        java.util.List<String> places = new java.util.ArrayList<>();
        if (!home.isEmpty()) places.add(home);
        String homeCity = home.split(",")[0].trim();
        for (String city : citiesIn(country)) {
            if (places.size() == 3) break;
            if (!city.split(",")[0].trim().equalsIgnoreCase(homeCity)) places.add(city);
        }
        return places;
    }
    private static String[] citiesIn(String country) {
        switch (country) {
            case "AU": return new String[]{"Sydney, Australia", "Melbourne, Australia", "Brisbane, Australia"};
            case "CN": return new String[]{"Shanghai, China", "Beijing, China", "Guangzhou, China"};
            case "US": return new String[]{"New York, United States", "Los Angeles, United States", "San Francisco, United States"};
            case "GB": return new String[]{"London, United Kingdom", "Manchester, United Kingdom", "Edinburgh, United Kingdom"};
            case "JP": return new String[]{"Tokyo, Japan", "Osaka, Japan", "Fukuoka, Japan"};
            case "NZ": return new String[]{"Auckland, New Zealand", "Wellington, New Zealand", "Christchurch, New Zealand"};
            case "CA": return new String[]{"Toronto, Canada", "Vancouver, Canada", "Montreal, Canada"};
            case "SG": return new String[]{"Singapore"};
            case "HK": return new String[]{"Hong Kong"};
            default: return new String[]{"Sydney, Australia", "Singapore", "London, United Kingdom"};
        }
    }
    /** 行程卡上的城市名；"Osaka, Japan" 只取 Osaka。 */
    static String place(JSONObject answer) {
        if (answer == null) return "";
        String label = answer.optString("label");
        int comma = label.indexOf(',');
        return comma < 0 ? label : label.substring(0, comma).trim();
    }
    static String dates(JSONObject answer) throws JSONException {
        if (answer == null || !"FIXED".equals(answer.optString("mode"))) return "";
        return TripDates.format(dateKey(answer.getString("startDate"))) + "  →  " + TripDates.format(dateKey(answer.getString("endDate")));
    }
    private static int dateKey(String iso) { return Integer.parseInt(iso.replace("-", "")); }
    static String isoDate(int key) { return String.format(java.util.Locale.US, "%04d-%02d-%02d", key / 10000, key / 100 % 100, key % 100); }
    static String companions(JSONObject answer) throws JSONException {
        int adults = answer.getInt("adults"), children = answer.getJSONArray("childrenAges").length();
        return adults + (adults == 1 ? " adult" : " adults") + (children == 0 ? "" : " · " + children + (children == 1 ? " child" : " children"));
    }
    static String budgetTitle(JSONObject answer) throws JSONException { return budgetAmount(answer).replace("Amount: ", ""); }
    static String budgetCaption(JSONObject answer) throws JSONException {
        return answer.getString("scope").toLowerCase(java.util.Locale.ROOT).replace('_', ' ')
                + (answer.getBoolean("includesFlights") ? " · flights included" : " · flights excluded");
    }
    /** 把要求、节奏和体验拆成一个个标签。 */
    static java.util.List<String> preferences(JSONObject steps) throws JSONException {
        java.util.List<String> values = new java.util.ArrayList<>();
        JSONObject constraints = steps.getJSONObject("CONSTRAINTS").optJSONObject("answer");
        if (constraints != null) {
            JSONArray requirements = constraints.getJSONArray("requirements");
            for (int i = 0; i < requirements.length(); i++) values.add(requirements.getJSONObject(i).getString("description"));
        }
        JSONObject pace = steps.getJSONObject("FOLLOW_UP_1").optJSONObject("answer");
        if (pace != null) values.add(pace.getString("description"));
        JSONObject experience = steps.getJSONObject("FOLLOW_UP_2").optJSONObject("answer");
        if (experience != null) {
            JSONArray items = experience.getJSONArray("preferences");
            for (int i = 0; i < items.length(); i++) values.add(items.getString(i));
        }
        return values;
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
