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

    JSONObject toJson() throws JSONException {
        // schema=1/2 是本机存储信封的版本，与后端 contract.schemaVersion=1.0 不同。
        JSONObject result = new JSONObject().put("schema", contractJson == null ? 1 : 2).put("id", id).put("createdAt", createdAt)
                .put("destination", destination).put("route", route).put("dates", dates)
                .put("totalDays", totalDays).put("brief", brief).put("itinerary", itinerary);
        // 还原为嵌套对象放入存储信封，不把整个 JSON 存成带转义引号的普通字段字符串。
        if (contractJson != null) result.put("contract", new JSONObject(contractJson));
        return result;
    }

    /** 后端返回完整 JSON 后调用此入口；这里只解析并创建快照，还需 TripStore.save 才落盘。 */
    static SavedTrip fromContract(Context context, String raw) throws JSONException {
        // 校验响应，再提取列表需要的摘要字段。
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
