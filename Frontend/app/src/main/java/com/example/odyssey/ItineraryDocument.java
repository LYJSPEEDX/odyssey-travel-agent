package com.example.odyssey;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

/**
 * 行程 JSON 的读取边界：检查结构、建立 ID 索引、核对跨对象引用。
 * JSON 对象本身仍可变，但本包按只读快照使用；此类不会验证航班/签证/价格是否真实。
 * 数据流：raw JSON → parse → ItineraryShape → ID 索引 → ItineraryRenderer。
 */
final class ItineraryDocument {
    // JSONObject 对应 JSON 的 {...}；JSONArray 对应 [...]；getXXX 读取必需字段，缺失会抛异常。
    final JSONObject root;
    // 用 ID 快速找到地点、事件、费用等对象，避免每画一张卡片都遍历所有数组。
    private final Map<String, JSONObject> entities = new HashMap<>();
    // entities: "place_sydney" → 地点对象；kinds: "place_sydney" → "places"，分别记录内容与类型。
    private final Map<String, String> kinds = new HashMap<>();
    // 私有构造器避免调用方绕过 parse 校验而直接 new 一个“已验证”文档。
    private ItineraryDocument(JSONObject root) { this.root = root; }

    static ItineraryDocument parse(Context context, String raw) throws JSONException {
        // 先限制输入规模/嵌套，再解析；形状正确不等于业务内容正确。
        ItineraryJson.preflight(raw, ItineraryAssets.MAX_BYTES);
        // 将文本变为可按键读取的对象树；这一步本身还不知道业务上必需哪些字段。
        JSONObject root = new JSONObject(raw);
        // Schema 是字段规则，不是另一份行程；从打包资源读取。
        JSONObject schema = ItineraryAssets.schema(context);
        // 两个 schema 参数分别是当前规则和完整规则根，后者用于解析 $ref；0 是初始深度。
        ItineraryShape.check(root, schema, schema, 0);
        ItineraryDocument doc = new ItineraryDocument(root);
        // 根对象也有 ID：暂时放入单元素数组，复用下面统一的 index 方法。
        doc.index(new JSONArray().put(root), "itinerary");
        for (String kind : new String[]{"places", "media", "transportGroups", "stayGroups", "days"})
            doc.index(root.getJSONArray(kind), kind);
        // 链式读取：先取 budget 对象，再取其 lineItems 数组，将费用 ID 纳入索引。
        doc.index(root.getJSONObject("budget").getJSONArray("lineItems"), "cost");
        doc.index(root.getJSONObject("money").getJSONArray("exchangeRates"), "exchange");
        doc.index(root.getJSONObject("quality").getJSONArray("sources"), "source");
        doc.index(root.getJSONObject("requestSnapshot").getJSONArray("travelers"), "traveler");
        doc.index(root.getJSONObject("requestSnapshot").getJSONArray("constraints"), "constraint");
        doc.index(root.getJSONArray("entryRequirements"), "entry");
        JSONArray days = root.getJSONArray("days");
        // 不能摘要写“10 天”，实际只返回 3 个 day；数组长度必须与声明一致。
        if (days.length() != root.getJSONObject("summary").getInt("dayCount")) ItineraryShape.fail("Day count mismatch");
        for (int i = 0; i < days.length(); i++) {
            JSONObject day = days.getJSONObject(i);
            // 数组第 0 项应声明 dayNumber=1，保证界面 Day N 与数据顺序一致。
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
                    // null 表示该角色尚未选出方案，可跳过；非 null 必须指向本组里的 option。
                    if (group.isNull(key)) continue;
                    boolean found = false;
                    for (int j = 0; j < options.length(); j++)
                        found |= options.getJSONObject(j).getString("id").equals(group.getString(key));
                    // 即使这个 ID 在别的组存在，也不能作为当前路线/住宿区间的选择。
                    if (!found) ItineraryShape.fail("Option outside group");
                }
                if (kind.equals("transportGroups")) for (int j = 0; j < options.length(); j++)
                    doc.index(options.getJSONObject(j).getJSONArray("legs"), "leg");
            }
        }
        // 必须在全部对象入索引后检查引用，否则合法的“引用后方对象”也会被误报。
        doc.references(root);
        return doc;
    }
    private void index(JSONArray array, String kind) throws JSONException {
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            String id = item.getString("id");
            // ID 在整份文档中唯一，不只是某个数组内唯一；重名会使引用产生歧义。
            // Map.put 返回旧值；非 null 说明此前已有同 ID，立刻失败而不是接受覆盖。
            if (entities.put(id, item) != null) ItineraryShape.fail("Duplicate ID");
            kinds.put(id, kind);
        }
    }
    /** 递归检查对象/数组里的引用字段；既要求 ID 存在，也要求其所属类型匹配。 */
    private void references(Object value) throws JSONException {
        // instanceof 检查实际类型；数组遍历每项，对象遍历每个字段，普通文本/数字无需继续。
        if (value instanceof JSONArray) {
            JSONArray items = (JSONArray) value;
            for (int i = 0; i < items.length(); i++) references(items.get(i));
        } else if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object child = object.get(key);
                // 例如 fromPlaceId 要指向 places，sourceIds 要指向 source；不是引用字段则返回 null。
                String kind = referenceKind(key);
                if (kind != null && child != JSONObject.NULL) {
                    if (child instanceof JSONArray) {
                        JSONArray ids = (JSONArray) child;
                        for (int i = 0; i < ids.length(); i++) require(ids.getString(i), kind);
                    } else require(object.getString(key), kind);
                }
                // 当前字段不是 ID 也可能包着更深的对象，继续递归检查内部引用。
                references(child);
            }
        }
    }
    private String referenceKind(String key) {
        // 外部地点供应商的 ID 不属于本 JSON 的内部引用，不应到 entities 中查找。
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
        // any 只要求 ID 存在；其余类型还要求一致，不能用酒店组 ID 冒充地点 ID。
        if (!(kind.equals("any") ? kinds.containsKey(id) : kind.equals(kinds.get(id)))) ItineraryShape.fail("Broken " + kind + " reference");
    }
    JSONObject get(String id) throws JSONException {
        // 查索引返回对象；找不到时显式抛异常，让页面显示读取失败而不是继续画错误内容。
        JSONObject value = entities.get(id);
        if (value == null) ItineraryShape.fail("Missing item");
        return value;
    }
    String place(String id) throws JSONException { return get(id).getString("name"); }
    JSONObject selected(JSONObject group) throws JSONException {
        // planningOptionId 才是当前排入日程/预算的方案；推荐或用户选择不一定已完成重算。
        // 返回 null 时渲染器显示方案待定；不能擅自拿 options[0] 充当已选方案。
        return group.isNull("planningOptionId") ? null : get(group.getString("planningOptionId"));
    }
}
