package com.example.odyssey;

import java.util.Currency;
import java.util.Locale;
import org.json.JSONObject;

/** 根据明确的居住国家识别币种；无法确定时返回空，不从国籍或城市猜测。 */
final class CurrencyPreference {
    static String fromCountry(String code) {
        if (code == null || code.length() != 2) return "";
        try {
            Currency currency = Currency.getInstance(new Locale("", code.toUpperCase(Locale.ROOT)));
            return currency == null || currency.getDefaultFractionDigits() < 0 ? "" : currency.getCurrencyCode();
        } catch (IllegalArgumentException ignored) { return ""; }
    }
    static String countryFromLocation(String location) {
        String value = InputValidation.text(location);
        if (value.isEmpty()) return "";
        for (String code : Locale.getISOCountries()) {
            String country = new Locale("en", code).getDisplayCountry(Locale.ENGLISH);
            if (value.equalsIgnoreCase(country) || value.toLowerCase(Locale.ROOT).endsWith(", " + country.toLowerCase(Locale.ROOT))) return code;
        }
        return "";
    }
    static String preferred(JSONObject profile) {
        if (profile == null) return "USD";
        String override = profile.optString("currency_override", "");
        if (!override.isEmpty()) return override;
        String country = profile.optString("residence_country_code", "");
        if (country.isEmpty()) country = countryFromLocation(profile.optString("residential_location", ""));
        String inferred = fromCountry(country);
        return inferred.isEmpty() ? "USD" : inferred;
    }
    private CurrencyPreference() { }
}
