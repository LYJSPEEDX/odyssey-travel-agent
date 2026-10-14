package com.example.odyssey;

import android.content.Context;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 保存时生成的只读快照：摘要用于列表，contractJson 用于结构化详情。
 * 重开行程只读取快照，不重新请求生成；contractJson 为 null 时兼容旧版文本行程。
 */
final class SavedTrip {
    // final 字段在构造时赋值，之后不能改引用；要更新内容应创建新的快照。
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
        // 重载构造器：旧记录没有 JSON，转交完整版构造器，并把 contractJson 设为 null。
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

    /** 把完成的五步表单转成本地 MOCK 行程；此入口不是后端模型调用。 */
    static SavedTrip fromPlan(Context context, TripPlanState state, String id) {
        if (!state.isGenerated()) throw new IllegalStateException("Only completed results can be saved");
        String dates = state.isFlexibleDates() ? context.getString(R.string.flexible_dates_summary)
                : context.getString(R.string.date_range, TripDates.format(state.getStartDate()),
                TripDates.format(state.getEndDate()));
        String budget = TripOptions.budgetDescription(state);
        // brief 是可读摘要，contract 是可按字段解析的 JSON，两者并非同一用途。
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
        // JSONObject.toString 把对象树序列化成文本，这时还没有写入磁盘。
        try { contract = MockItinerary.fromPlan(context, state, id).toString(); }
        catch (JSONException error) { throw new IllegalArgumentException("Cannot create itinerary", error); }
        // currentTimeMillis 是快照创建时间，用于列表排序，不是旅行出发时间。
        return new SavedTrip(id, System.currentTimeMillis(), state.getDestination(),
                context.getString(R.string.trip_route, state.getDeparture(), state.getDestination()),
                dates, state.isFlexibleDates() ? 3 : state.getDurationDays(),
                brief.toString(), "", contract);
    }

    JSONObject toJson() throws JSONException {
        // schema=1/2 是本机存储信封的版本，与后端 contract.schemaVersion=1.0 不同。
        JSONObject result = new JSONObject().put("schema", contractJson == null ? 1 : 2).put("id", id).put("createdAt", createdAt)
                .put("destination", destination).put("route", route).put("dates", dates)
                .put("totalDays", totalDays).put("brief", brief).put("itinerary", itinerary);
        // 还原为嵌套对象放入存储信封，不把整个 JSON 存成带转义引号的普通字段字符串。
        if (contractJson != null) result.put("contract", new JSONObject(contractJson));
        return result;
    }

    /** 未来接口返回完整 JSON 后可调用此入口；这里只解析并创建快照，还需 TripStore.save 才落盘。 */
    static SavedTrip fromContract(Context context, String raw) throws JSONException {
        // 真实模型响应应走此入口而非 fromPlan：校验响应，再提取列表需要的摘要字段。
        ItineraryDocument document = ItineraryDocument.parse(context, raw);
        JSONObject root = document.root, summary = root.getJSONObject("summary"), request = root.getJSONObject("requestSnapshot");
        org.json.JSONArray destinations = request.getJSONArray("destinationPlaceIds");
        StringBuilder route = new StringBuilder(document.place(request.getString("originPlaceId")));
        String destination = "";
        for (int i = 0; i < destinations.length(); i++) {
            destination = document.place(destinations.getString(i));
            route.append(" → ").append(destination);
        }
        return new SavedTrip(root.getString("id"), System.currentTimeMillis(), destination, route.toString(),
                summary.getString("startDate") + " → " + summary.getString("endDate"), summary.getInt("dayCount"),
                summary.getString("subtitle"), "", root.toString());
    }

    static SavedTrip fromJson(String value) throws JSONException {
        // 先验证存储信封；完整 contract 在详情展示或 fromContract 入口再做结构/引用校验。
        ItineraryJson.preflight(value, ItineraryAssets.MAX_BYTES * 2);
        JSONObject object = new JSONObject(value);
        int version = object.getInt("schema");
        if (version != 1 && version != 2) throw new JSONException("Unsupported saved trip schema");
        String contract = version == 2 ? object.getJSONObject("contract").toString() : null;
        // 外层快照 ID 必须与内层行程 ID 相同，避免列表摘要和详情指向不同记录。
        if (contract != null && !object.getString("id").equals(object.getJSONObject("contract").getString("id")))
            throw new JSONException("Snapshot ID mismatch");
        return new SavedTrip(object.getString("id"), object.getLong("createdAt"),
                object.getString("destination"), object.getString("route"), object.getString("dates"),
                object.getInt("totalDays"), object.getString("brief"), object.getString("itinerary"), contract);
    }
}
