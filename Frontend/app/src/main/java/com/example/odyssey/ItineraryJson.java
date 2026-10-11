package com.example.odyssey;

import org.json.JSONException;

/** Bound parser recursion BEFORE JSONTokener allocates a recursively nested structure. */
final class ItineraryJson {
    static void preflight(String raw, int limit) throws JSONException {
        if (raw == null || raw.length() > limit) throw new JSONException("JSON length limit");
        boolean quoted = false, escaped = false;
        int depth = 0;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if (ch == '"') quoted = false;
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
