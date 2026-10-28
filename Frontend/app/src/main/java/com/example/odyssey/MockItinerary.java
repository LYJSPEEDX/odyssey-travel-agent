package com.example.odyssey;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 将用户表单转成与后端协议形状一致的本地草稿，供 UI 联调，不是模型输出或可预订方案。
 * 已知偏好保留，未知交通/酒店/价格保持 PENDING 或空值，不凭空补全真实信息。
 */
final class MockItinerary {
    static JSONObject object(Object... pairs) throws JSONException {
        // 可变参数按“键、值”成对传入；JSONObject.NULL 保留显式 null 字段而非删除该键。
        JSONObject result = new JSONObject();
        for (int i = 0; i < pairs.length; i += 2)
            result.put((String) pairs[i], pairs[i + 1] == null ? JSONObject.NULL : pairs[i + 1]);
        return result;
    }
    static JSONArray array(Object... values) { JSONArray result = new JSONArray(); for (Object v : values) result.put(v); return result; }
    static JSONObject evidence() throws JSONException { return object("status", "MOCK", "sourceIds", array(), "checkedAt", null); }
    static String date(Calendar calendar) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(calendar.getTime());
    }
    static JSONObject place(String id, String name) throws JSONException {
        // 城市尚未经过地点服务查询，ZZ 与 Etc/UTC 只是占位，不能当作真实国家/当地时区。
        return object("id", id, "name", name, "kind", "CITY", "countryCode", "ZZ", "timeZone", "Etc/UTC",
                "address", null, "coordinates", null, "providerPlaceId", null, "imageIds", array(), "evidence", evidence());
    }
    static JSONObject fromPlan(Context context, TripPlanState state, String id) throws JSONException {
        // 灵活日期暂用“一周后三天”演示；固定日期按完整天数生成，不截成三天预览。
        int count = state.isFlexibleDates() ? 3 : state.getDurationDays();
        if (count < 1 || count > 60) throw new JSONException("Supported duration is 1–60 days");
        Calendar start = state.isFlexibleDates() ? Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                : TripDates.calendar(state.getStartDate());
        if (state.isFlexibleDates()) start.add(Calendar.DAY_OF_MONTH, 7);
        Calendar end = (Calendar) start.clone(); end.add(Calendar.DAY_OF_MONTH, count - 1);
        JSONArray notes = array("Budget style: " + state.getBudget(), "Companion choice: " + state.getCompanion());
        for (int step = 1; step <= 5; step++) if (!state.getNote(step).isEmpty()) notes.put(state.getNote(step));
        JSONArray constraints = array(), checks = array();
        int index = 0;
        for (String label : state.getConstraints()) {
            String code;
            switch (label) {
                case "Vegetarian": code = "VEGETARIAN"; break;
                case "With pets": code = "PET_FRIENDLY"; break;
                case "With older adults": code = "SENIOR_FRIENDLY"; break;
                case "Less walking": code = "LOW_WALKING"; break;
                case "See more": code = "SEE_MORE"; break;
                case "Take it slow": code = "RELAXED"; break;
                default: code = "CUSTOM";
            }
            String constraintId = "constraint_" + index++;
            constraints.put(object("id", constraintId, "code", code, "strength", "SOFT", "description", label));
            checks.put(object("constraintId", constraintId, "status", "UNVERIFIED",
                    "explanation", "Saved preference; real venues and routes have not been checked.", "relatedIds", array()));
        }
        String companion = state.getCompanion().toUpperCase(Locale.ROOT);
        if (!companion.matches("SOLO|COUPLE|FRIENDS|FAMILY")) companion = "CUSTOM";
        JSONArray days = array();
        // 每天只放自由活动占位；生成 days 数组展示结构，不伪造景点、餐厅或开放时间。
        Calendar current = (Calendar) start.clone();
        for (int i = 0; i < count; i++, current.add(Calendar.DAY_OF_MONTH, 1)) {
            String dayDate = date(current);
            String description = "Plan activities in " + state.getDestination() + ". Venues, transfers and meal options are pending.";
            if (!state.getConstraints().isEmpty()) description += " Preferences: " + String.join(", ", state.getConstraints()) + ".";
            JSONObject event = object("id", "event_" + i, "type", "FREE_TIME", "title", "Time to explore",
                    "description", description, "imageIds", array(),
                    "start", object("at", dayDate + "T10:00:00Z", "timeZone", "Etc/UTC"),
                    "end", object("at", dayDate + "T12:00:00Z", "timeZone", "Etc/UTC"),
                    "timingStatus", "ESTIMATED", "evidence", evidence(), "placeId", "destination", "costItemIds", array());
            days.put(object("id", "day_" + i, "dayNumber", i + 1, "date", dayDate, "timeZone", "Etc/UTC",
                    "title", "Discover " + state.getDestination(), "summary", "Draft day — arrange details with your travel assistant.",
                    "walkingDistanceMeters", null, "events", array(event), "continuingEventIds", array()));
        }
        // 空费用表的零小计表示尚未计价；UI 结合 activeCostItemIds 显示 Estimate pending。
        String displayCurrency = "USD";
        try { displayCurrency = CurrencyPreference.preferred(new ProfileStore(context.getApplicationContext()).load()); }
        catch (Exception ignored) { /* 资料无法读取时使用 USD，不根据国籍猜测。 */ }
        int fractionDigits = java.util.Currency.getInstance(displayCurrency).getDefaultFractionDigits();
        JSONObject zero = object("currency", displayCurrency, "lowMinor", 0, "expectedMinor", 0, "highMinor", 0);
        SimpleDateFormat timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        timestamp.setTimeZone(TimeZone.getTimeZone("UTC"));
        JSONArray assumptions = array("The traveler list has one placeholder; actual party size and ages are not collected yet.",
                "ZZ and Etc/UTC are draft placeholders, not resolved country or destination local time.",
                displayCurrency + " is the preferred display currency. No prices or exchange rates have been fetched.");
        if (state.isFlexibleDates()) assumptions.put("Proposed dates: three days starting one week from today; not a booking.");
        JSONObject result = object("schemaVersion", "2.0", "contractId", "odyssey.frontend.itinerary", "id", id, "revision", 1, "language", "en", "dataMode", "MOCK",
                "status", "PARTIAL", "generatedAt", timestamp.format(new Date()),
                "requestSnapshot", object("originPlaceId", "origin", "destinationPlaceIds", array("destination"),
                        "dateMode", state.isFlexibleDates() ? "FLEXIBLE" : "FIXED",
                        "requestedStartDate", state.isFlexibleDates() ? null : date(start),
                        "requestedEndDate", state.isFlexibleDates() ? null : date(end), "returnDateMeaning", "ARRIVE_HOME",
                        "companions", companion, "travelers", array(object("id", "traveler_1", "age", null,
                                "passportCountry", null, "passportType", null, "residenceCountry", null)),
                        "budget", object("scope", "ENTIRE_PARTY_WHOLE_TRIP", "target", null, "includeIntercityTransport", true),
                        "constraints", constraints, "notes", notes),
                "summary", object("title", state.getDeparture() + " → " + state.getDestination(),
                        "subtitle", "Your local planning draft", "startDate", date(start), "endDate", date(end),
                        "dayCount", count, "coverImageId", null, "highlights", array(state.getCompanion(), state.getBudget()),
                        "advice", array("This draft is not a bookable itinerary. Confirm routes, prices and entry requirements before travel.")),
                "places", array(place("origin", state.getDeparture()), place("destination", state.getDestination())), "media", array(),
                "transportGroups", array(object("id", "outbound", "title", "Getting there", "fromPlaceId", "origin",
                        "toPlaceId", "destination", "status", "PENDING", "recommendedOptionId", null,
                        "planningOptionId", null, "userSelectedOptionId", null, "options", array()),
                        object("id", "return", "title", "Getting home", "fromPlaceId", "destination", "toPlaceId", "origin",
                                "status", "PENDING", "recommendedOptionId", null, "planningOptionId", null,
                                "userSelectedOptionId", null, "options", array())),
                "stayGroups", array(), "days", days,
                "money", object("displayCurrency", displayCurrency, "currencies", array(object("code", displayCurrency, "minorUnitDigits", fractionDigits)),
                        "exchangeRates", array(), "exchangeAdvice", array()), "entryRequirements", array(),
                "budget", object("scope", "ENTIRE_PARTY_WHOLE_TRIP", "lineItems", array(), "tripCostItemIds", array(),
                        "activeCostItemIds", array(), "completeness", "PARTIAL", "subtotal", zero, "contingency", zero,
                        "total", zero, "excludedCategories", array("Transport", "Accommodation", "Food", "Activities"),
                        "notes", array("No priced items yet. A zero known subtotal is not a free trip.")),
                "quality", object("sources", array(), "assumptions", assumptions,
                        "notices", array(object("code", "MOCK_DRAFT", "severity", "WARNING",
                                "message", "Dates and times are illustrative. Transport, stays, visas and prices still need verification.", "relatedIds", array())),
                        "constraintChecks", checks));
        JSONObject cover = MockPhotoCatalog.media(state.getDestination());
        if (cover != null) { result.getJSONArray("media").put(cover); result.getJSONObject("summary").put("coverImageId", "mock_cover"); }
        // 假数据也走真实解析边界，让字段缺失/引用错误在开发时暴露，而不是 UI 崩溃时才发现。
        ItineraryDocument.parse(context, result.toString());
        return result;
    }
    private MockItinerary() {}
}
