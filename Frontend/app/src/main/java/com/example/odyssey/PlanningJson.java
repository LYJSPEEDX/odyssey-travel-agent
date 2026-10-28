package com.example.odyssey;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Collection;
import java.util.UUID;

/** 唯一的本地会话 JSON 边界；UI 不读取任意 JSON 字段，也不接受未经关联校验的快照。 */
final class PlanningJson {
    static final int MAX_CHARS = 100000;
    static JSONObject textInput(String text) throws JSONException {
        return new JSONObject().put("type", "TEXT").put("text", InputValidation.requiredText(text, 500, "Message"));
    }
    static JSONObject optionsInput(Collection<String> ids) throws JSONException {
        return new JSONObject().put("type", "OPTIONS").put("optionIds", new JSONArray(ids));
    }
    static JSONObject dateInput(TripPlanState dates) throws JSONException {
        return new JSONObject().put("type", "DATE_RANGE").put("startDate", dates.getStartDate()).put("endDate", dates.getEndDate());
    }
    static JSONObject request(PlanningSession s, String action, JSONObject input) throws JSONException {
        return new JSONObject().put("schemaVersion", 1).put("requestId", UUID.randomUUID().toString())
                .put("sessionId", s.id).put("baseRevision", s.revision).put("stepId", PlanningSession.IDS[s.current])
                .put("questionId", s.step().question.id).put("action", action).put("input", input);
    }
    static JSONObject snapshot(PlanningSession s) throws JSONException {
        JSONObject profileContext = new JSONObject().put("residence", s.residence).put("preferences", s.preferences)
                .put("registrationProfile", (s.profileRequest == null || s.profileRequest.isEmpty()) ? JSONObject.NULL : new JSONObject(s.profileRequest));
        JSONObject root = new JSONObject().put("id", s.id).put("revision", s.revision).put("dataMode", "MOCK")
                .put("profileContext", profileContext)
                .put("canGenerate", s.canGenerate()).put("current", s.current).put("feedback", s.feedback)
                .put("dateDraft", new JSONObject().put("startDate", s.dateDraft.getStartDate())
                        .put("endDate", s.dateDraft.getEndDate()).put("flexible", s.dateDraft.isFlexibleDates()));
        JSONArray steps = new JSONArray();
        for (int i = 0; i < s.steps.length; i++) {
            PlanningSession.Step step = s.steps[i]; PlanningSession.Question q = step.question;
            JSONArray options = new JSONArray();
            for (PlanningSession.Option o : q.options) options.put(new JSONObject().put("id", o.id).put("title", o.title)
                    .put("description", o.description).put("keywords", new JSONArray(o.keywords))
                    .put("imageUrl", o.imageUrl == null ? JSONObject.NULL : o.imageUrl).put("value", o.value));
            JSONObject question = new JSONObject().put("id", q.id).put("title", q.title).put("description", q.description)
                    .put("inputMode", q.inputMode).put("allowText", q.allowText).put("minSelections", q.minSelections)
                    .put("maxSelections", q.maxSelections).put("options", options);
            JSONArray messages = new JSONArray();
            for (PlanningSession.Message message : step.messages)
                messages.put(new JSONObject().put("role", message.role).put("text", message.text));
            steps.put(new JSONObject().put("messages", messages).put("chatCandidate", step.chatCandidate).put("stepId", PlanningSession.IDS[i]).put("state", step.state).put("question", question)
                    .put("answer", step.answer).put("draft", step.draft).put("keywords", new JSONArray(step.keywords))
                    .put("selected", new JSONArray(step.selected)));
        }
        return root.put("steps", steps);
    }
    static PlanningSession parse(String raw) throws JSONException {
        ItineraryJson.preflight(raw, MAX_CHARS);
        JSONObject root = new JSONObject(raw);
        JSONObject profile = root.getJSONObject("profileContext");
        PlanningSession s = new PlanningSession(string(root, "id", 100), string(profile, "residence", 200), string(profile, "preferences", 2000));
        if (!profile.isNull("registrationProfile") && profile.has("registrationProfile"))
            s.profileRequest = profile.getJSONObject("registrationProfile").toString();
        s.revision = integer(root, "revision", 0, Integer.MAX_VALUE);
        s.current = integer(root, "current", 0, 7);
        if (!string(root, "dataMode", 10).equals("MOCK")) throw new IllegalArgumentException("Not a Mock session");
        s.feedback = string(root, "feedback", 3000);
        JSONArray steps = root.getJSONArray("steps");
        if (steps.length() != 8) throw new IllegalArgumentException("Expected eight fixed slots");
        for (int i = 0; i < 8; i++) {
            JSONObject item = steps.getJSONObject(i); PlanningSession.Step step = s.steps[i];
            if (!string(item, "stepId", 30).equals(PlanningSession.IDS[i])) throw new IllegalArgumentException("Slot order changed");
            step.state = choice(item, "state", "LOCKED", "AWAITING_INPUT", "CONFIRMED", "NEEDS_REVIEW");
            step.answer = string(item, "answer", 2000); step.draft = string(item, "draft", 500);
            step.chatCandidate = item.has("chatCandidate") ? string(item, "chatCandidate", 500) : "";
            JSONArray messages = item.optJSONArray("messages");
            if (messages != null) {
                if (messages.length() > 12) throw new IllegalArgumentException("Too many chat messages");
                for (int j = 0; j < messages.length(); j++) {
                    JSONObject message = messages.getJSONObject(j);
                    step.messages.add(new PlanningSession.Message(choice(message, "role", "USER", "ASSISTANT"),
                            InputValidation.requiredText(message.opt("text"), 500, "Message")));
                }
            }
            readStrings(item.getJSONArray("keywords"), step.keywords, 20, 500);
            readStrings(item.getJSONArray("selected"), step.selected, 8, 150);
            JSONObject value = item.getJSONObject("question"); PlanningSession.Question q = new PlanningSession.Question();
            q.id = string(value, "id", 150); q.title = string(value, "title", 300); q.description = string(value, "description", 3000);
            q.inputMode = choice(value, "inputMode", "OPTIONS", "DATE_RANGE"); q.allowText = bool(value, "allowText");
            q.minSelections = integer(value, "minSelections", 0, 8); q.maxSelections = integer(value, "maxSelections", 1, 8);
            if (q.minSelections > q.maxSelections) throw new IllegalArgumentException("Invalid selection bounds");
            JSONArray options = value.getJSONArray("options");
            if (options.length() > 12) throw new IllegalArgumentException("Too many options");
            java.util.Set<String> ids = new java.util.HashSet<>();
            for (int n = 0; n < options.length(); n++) {
                JSONObject o = options.getJSONObject(n);
                PlanningSession.Option option = new PlanningSession.Option(string(o, "id", 150), string(o, "title", 300),
                        string(o, "description", 1000), string(o, "value", 500));
                if (!ids.add(option.id)) throw new IllegalArgumentException("Duplicate option");
                readStrings(o.getJSONArray("keywords"), option.keywords, 20, 200);
                if (i == 1 && option.keywords.size() != 3) throw new IllegalArgumentException("Destination needs three keywords");
                // 可选坏图片不应拒绝整个已完成的行程回答；远程加载边界再验证 URL。
                option.imageUrl = ResponseImage.text(o.opt("imageUrl"), 2000);
                q.options.add(option);
            }
            step.question = q;
        }
        JSONObject dates = root.getJSONObject("dateDraft");
        int start = integer(dates, "startDate", 0, 99991231), end = integer(dates, "endDate", 0, 99991231);
        if (bool(dates, "flexible")) s.dateDraft.useFlexibleDates();
        else if (start != 0) { s.dateDraft.selectDate(start); if (end != 0) s.dateDraft.selectDate(end); }
        if (bool(root, "canGenerate") != s.canGenerate()) throw new IllegalArgumentException("Inconsistent generation state");
        return s;
    }
    /** 请求号、会话号、版本、当前槽位与问题必须同时匹配，晚到的回包不可劫持浏览位置。 */
    static PlanningSession apply(PlanningSession current, JSONObject request, String raw) throws JSONException {
        return applyResult(current, request, raw).session;
    }
    /** UI 接收经过同一边界验证的导航结果，不自行解析 result JSON 或用资料完整度猜导航。 */
    static final class Applied {
        final PlanningSession session;
        final String navigation;
        Applied(PlanningSession session, String navigation) { this.session = session; this.navigation = navigation; }
    }
    static Applied applyResult(PlanningSession current, JSONObject request, String raw) throws JSONException {
        ItineraryJson.preflight(raw, MAX_CHARS);
        JSONObject response = new JSONObject(raw), result = response.getJSONObject("result");
        if (integer(response, "schemaVersion", 1, 1) != 1
                || !string(response, "requestId", 100).equals(string(request, "requestId", 100))
                || !string(request, "sessionId", 100).equals(current.id)
                || integer(request, "baseRevision", 0, Integer.MAX_VALUE) != current.revision
                || !string(request, "stepId", 30).equals(PlanningSession.IDS[current.current])
                || !string(request, "questionId", 150).equals(current.step().question.id))
            throw new IllegalArgumentException("Outdated response");
        String status = choice(result, "status", "UPDATED", "ACCEPTED", "CLARIFY");
        String navigation = choice(result, "navigation", "STAY", "NEXT", "GENERATE");
        if (!string(result, "stepId", 30).equals(PlanningSession.IDS[current.current])) throw new IllegalArgumentException("Wrong result slot");
        java.util.List<String> keywords = new java.util.ArrayList<>();
        readStrings(result.getJSONArray("keywords"), keywords, 20, 500);
        PlanningSession next = parse(response.getJSONObject("session").toString());
        if (!next.id.equals(current.id) || next.revision != current.revision + 1
                || !next.residence.equals(current.residence) || !next.preferences.equals(current.preferences)
                || !sameProfile(current.profileRequest, next.profileRequest))
            throw new IllegalArgumentException("Wrong session revision");
        int expected = navigation.equals("NEXT") ? Math.min(7, current.current + 1) : current.current;
        if (next.current != expected || navigation.equals("GENERATE") && (current.current != 7 || !next.canGenerate())
                || status.equals("UPDATED") && !navigation.equals("STAY")
                || status.equals("ACCEPTED") && !next.steps[current.current].state.equals("CONFIRMED"))
            throw new IllegalArgumentException("Invalid navigation");
        Object nextId = result.opt("nextStepId");
        if (navigation.equals("NEXT") ? !PlanningSession.IDS[expected].equals(nextId) : nextId != JSONObject.NULL)
            throw new IllegalArgumentException("Invalid next slot");
        return new Applied(next, navigation);
    }
    /** 拒绝响应静默更改已固定的资料快照；不要在这里记录个人信息。 */
    private static boolean sameProfile(String left, String right) throws JSONException {
        if (left.isEmpty() || right.isEmpty()) return left.equals(right);
        return jsonEqual(new JSONObject(left), new JSONObject(right));
    }
    private static boolean jsonEqual(Object left, Object right) throws JSONException {
        if (left instanceof JSONObject && right instanceof JSONObject) {
            JSONObject a = (JSONObject) left, b = (JSONObject) right;
            if (a.length() != b.length()) return false;
            java.util.Iterator<String> keys = a.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!b.has(key) || !jsonEqual(a.get(key), b.get(key))) return false;
            }
            return true;
        }
        if (left instanceof JSONArray && right instanceof JSONArray) {
            JSONArray a = (JSONArray) left, b = (JSONArray) right;
            if (a.length() != b.length()) return false;
            for (int i = 0; i < a.length(); i++) if (!jsonEqual(a.get(i), b.get(i))) return false;
            return true;
        }
        return left == right || left != null && left.equals(right);
    }
    static String string(JSONObject o, String key, int limit) {
        return InputValidation.typedText(o.opt(key), limit, key);
    }
    static int integer(JSONObject o, String key, int min, int max) {
        Object value = o.opt(key);
        if (!(value instanceof Integer) || (Integer) value < min || (Integer) value > max) throw new IllegalArgumentException("Invalid " + key);
        return (Integer) value;
    }
    static boolean bool(JSONObject o, String key) {
        Object value = o.opt(key); if (!(value instanceof Boolean)) throw new IllegalArgumentException("Invalid " + key); return (Boolean) value;
    }
    static String choice(JSONObject o, String key, String... options) {
        String value = InputValidation.requiredText(o.opt(key), 30, key);
        if (!java.util.Arrays.asList(options).contains(value)) throw new IllegalArgumentException("Invalid " + key); return value;
    }
    static void readStrings(JSONArray array, Collection<String> target, int max, int length) throws JSONException {
        if (array.length() > max) throw new IllegalArgumentException("Too many values");
        for (int i = 0; i < array.length(); i++) target.add(InputValidation.requiredText(array.get(i), length, "Value"));
    }
    private PlanningJson() { }
}
