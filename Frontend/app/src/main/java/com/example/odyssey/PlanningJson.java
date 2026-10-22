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
        JSONObject root = new JSONObject().put("id", s.id).put("revision", s.revision).put("dataMode", "MOCK")
                .put("profileContext", new JSONObject().put("residence", s.residence).put("preferences", s.preferences))
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
            steps.put(new JSONObject().put("stepId", PlanningSession.IDS[i]).put("state", step.state).put("question", question)
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
                if (!o.isNull("imageUrl")) option.imageUrl = string(o, "imageUrl", 2000);
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
                || !next.residence.equals(current.residence) || !next.preferences.equals(current.preferences))
            throw new IllegalArgumentException("Wrong session revision");
        int expected = navigation.equals("NEXT") ? Math.min(7, current.current + 1) : current.current;
        if (next.current != expected || navigation.equals("GENERATE") && !next.canGenerate()
                || status.equals("UPDATED") && !navigation.equals("STAY")
                || status.equals("ACCEPTED") && !next.steps[current.current].state.equals("CONFIRMED"))
            throw new IllegalArgumentException("Invalid navigation");
        Object nextId = result.opt("nextStepId");
        if (navigation.equals("NEXT") ? !PlanningSession.IDS[expected].equals(nextId) : nextId != JSONObject.NULL)
            throw new IllegalArgumentException("Invalid next slot");
        return next;
    }
    static String string(JSONObject o, String key, int limit) {
        if (!(o.opt(key) instanceof String)) throw new IllegalArgumentException("Invalid " + key);
        return InputValidation.optionalText(o.opt(key), limit, key);
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
