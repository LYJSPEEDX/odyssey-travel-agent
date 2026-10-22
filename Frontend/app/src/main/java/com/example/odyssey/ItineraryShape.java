package com.example.odyssey;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * 只实现当前打包 Schema 所需的一部分结构检查，不是完整的 JSON Schema 引擎。
 * 检查类型、必填项、枚举及部分范围；不验证 format、真实行程可行性、证据或预算运算。
 * 后端仍须进行完整协议及业务校验，不能因本类通过就认为模型结果可信。
 */
final class ItineraryShape {
    static void check(Object value, JSONObject schema, JSONObject root, int depth) throws JSONException {
        if (depth > 40) fail("Nesting limit");
        if (schema.has("$ref")) {
            // 仅解析本地 $defs 定义，不下载或执行外部 Schema 引用。
            String ref = schema.getString("$ref");
            if (!ref.startsWith("#/$defs/")) fail("Unsupported reference");
            check(value, root.getJSONObject("$defs").getJSONObject(ref.substring(8)), root, depth + 1);
            return;
        }
        for (String variant : new String[]{"anyOf", "oneOf"}) {
            // anyOf 至少匹配一种结构，oneOf 必须恰好匹配一种，例如不同交通方式的 details。
            if (!schema.has(variant)) continue;
            JSONArray choices = schema.getJSONArray(variant);
            int matches = 0;
            for (int i = 0; i < choices.length(); i++) {
                try { check(value, choices.getJSONObject(i), root, depth + 1); matches++; }
                catch (JSONException ignored) { /* Try the next explicitly allowed shape. */ }
            }
            if (matches == 0 || (variant.equals("oneOf") && matches != 1)) fail("Invalid variant");
            return;
        }
        if (schema.has("const") && !schema.get("const").equals(value)) fail("Unsupported constant");
        if (schema.has("enum")) {
            boolean found = false;
            JSONArray values = schema.getJSONArray("enum");
            for (int i = 0; i < values.length(); i++) found |= values.get(i).equals(value);
            if (!found) fail("Unknown enum");
        }
        String type = schema.optString("type");
        boolean valid = type.isEmpty() || (type.equals("null") && value == JSONObject.NULL)
                || (type.equals("object") && value instanceof JSONObject)
                || (type.equals("array") && value instanceof JSONArray)
                || (type.equals("string") && value instanceof String)
                || (type.equals("boolean") && value instanceof Boolean)
                || (type.equals("number") && value instanceof Number)
                || (type.equals("integer") && value instanceof Number
                    && ((Number) value).doubleValue() == Math.rint(((Number) value).doubleValue()));
        if (!valid) fail("Incorrect field type");
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            JSONArray required = schema.optJSONArray("required");
            // “字段存在”和“字段值可为 null”不同；has 检查前者，后续类型分支检查后者。
            if (required != null) for (int i = 0; i < required.length(); i++)
                if (!object.has(required.getString(i))) fail("Missing " + required.getString(i));
            JSONObject properties = schema.optJSONObject("properties");
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (properties != null && properties.has(key))
                    check(object.get(key), properties.getJSONObject(key), root, depth + 1);
                else if (!schema.optBoolean("additionalProperties", true)) fail("Unknown field " + key);
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            if (array.length() < schema.optInt("minItems", 0)
                    || array.length() > schema.optInt("maxItems", 10000)) fail("Array length");
            Set<String> unique = new HashSet<>();
            for (int i = 0; i < array.length(); i++) {
                if (schema.optBoolean("uniqueItems") && !unique.add(array.get(i).toString())) fail("Duplicate item");
                if (schema.has("items")) check(array.get(i), schema.getJSONObject("items"), root, depth + 1);
            }
        } else if (value instanceof String) {
            String text = (String) value;
            if (text.codePointCount(0, text.length()) < schema.optInt("minLength", 0)
                    || text.codePointCount(0, text.length()) > schema.optInt("maxLength", 20000)) fail("Text length");
            if (schema.has("pattern") && !java.util.regex.Pattern.compile(schema.getString("pattern")).matcher(text).find()) fail("Text format");
        } else if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (!Double.isFinite(number) || number < schema.optDouble("minimum", -Double.MAX_VALUE)
                    || number > schema.optDouble("maximum", Double.MAX_VALUE)) fail("Number range");
        }
    }
    static void fail(String message) throws JSONException { throw new JSONException(message); }
    private ItineraryShape() {}
}
