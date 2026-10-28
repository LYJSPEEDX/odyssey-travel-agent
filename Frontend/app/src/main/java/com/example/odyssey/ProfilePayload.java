package com.example.odyssey;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** 本机资料到未来会话请求的唯一映射；不执行网络请求，也不改变本机资料。 */
final class ProfilePayload {
    static JSONObject createSession(JSONObject local) throws JSONException {
        if (!ProfileStore.isComplete(local)) throw new IllegalArgumentException("Complete the profile first");
        String uid = InputValidation.uid(local.opt("uid"));
        String location = InputValidation.optionalText(local.opt("residential_location"), 200, "Residence");
        String country = InputValidation.countryCode(local.opt("residence_country_code"));
        if (country.isEmpty()) country = CurrencyPreference.countryFromLocation(location);
        String override = InputValidation.currencyCode(local.opt("currency_override"));
        String inferred = CurrencyPreference.fromCountry(country);
        String currency = override.isEmpty() ? (inferred.isEmpty() ? "USD" : inferred) : override;
        String currencySource = !override.isEmpty() ? "MANUAL" : inferred.isEmpty() ? "DEFAULT" : "RESIDENCE";
        String gender = InputValidation.optionalText(local.opt("gender"), 200, "Gender");
        if (gender.equals("Prefer not to say") || gender.equals("Prefer not to tell")) gender = "";
        Object age = local.has("legacy_age") ? local.opt("legacy_age") : local.opt("age");
        Object legacyAge = JSONObject.NULL;
        if (age != null && age != JSONObject.NULL) {
            if (!(age instanceof Integer)) throw new IllegalArgumentException("Invalid legacy age");
            legacyAge = InputValidation.age(age.toString());
        }
        JSONArray selected = local.optJSONArray("preference_tags");
        if (selected == null) throw new IllegalArgumentException("Missing preferences");
        // 复制数组以保证请求是快照；本地偏好以后被编辑也不会改变已经创建的请求。
        JSONArray tags = new JSONArray();
        for (int i = 0; i < selected.length(); i++) tags.put(InputValidation.requiredText(selected.get(i), 50, "Preference tag"));
        JSONObject profile = new JSONObject().put("uid", uid)
                .put("displayName", optional(local.opt("name"), 200, "Name"))
                .put("gender", gender.isEmpty() ? JSONObject.NULL : gender)
                .put("birthDate", optional(local.opt("birth_date"), 10, "Date of birth"))
                .put("legacyAgeYears", legacyAge)
                .put("nationality", optional(local.opt("nationality"), 200, "Nationality"))
                .put("residentialLocation", location.isEmpty() ? JSONObject.NULL : location)
                .put("residenceCountryCode", country.isEmpty() ? JSONObject.NULL : country)
                .put("displayCurrency", currency).put("currencySource", currencySource)
                .put("travelTagIds", tags)
                .put("travelPreferences", optional(local.opt("travel_preferences"), 2000, "Preferences"));
        return new JSONObject().put("schemaVersion", "1.0").put("profile", profile);
    }
    private static Object optional(Object value, int max, String label) {
        String text = InputValidation.optionalText(value, max, label);
        return text.isEmpty() ? JSONObject.NULL : text;
    }
    private ProfilePayload() { }
}
