package com.example.odyssey;

import org.json.JSONException;

/**
 * JSON 解析前的轻量检查，先限制文本长度和嵌套，避免解析器处理过深结构。
 * 不是完整 JSON 语法验证器；后面仍需 JSONObject 解析、形状校验和引用检查。
 */
final class ItineraryJson {
    static void preflight(String raw, int limit) throws JSONException {
        // 此处 length 是 Java 字符串长度；资产文件的真实字节限制由 ItineraryAssets 另行检查。
        if (raw == null || raw.length() > limit) throw new JSONException("JSON length limit");
        boolean quoted = false, escaped = false;
        int depth = 0;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (quoted) {
                // 引号内的大括号不算嵌套；转义引号也不能被误判为字符串结束。
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if (ch == '"') quoted = false;
            } else if (ch == '\'' || ch == '/' || ch == '#') {
                // Android's lenient JSONTokener supports single quotes/comments; our contract does not.
                throw new JSONException("Non-standard JSON syntax");
            } else if (ch == '"') quoted = true;
            else if (ch == '{' || ch == '[') {
                if (++depth > 30) throw new JSONException("JSON nesting limit");
            } else if (ch == '}' || ch == ']') {
                if (--depth < 0) throw new JSONException("Unbalanced JSON");
            }
        }
        if (quoted || depth != 0) throw new JSONException("Incomplete JSON");
    }
    private ItineraryJson() {}
}
