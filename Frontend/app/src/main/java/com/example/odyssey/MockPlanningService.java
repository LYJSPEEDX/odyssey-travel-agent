package com.example.odyssey;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** 本地确定性演示服务：收发 JSON 字符串，不访问网络、不承诺后端协议兼容。 */
final class MockPlanningService {
    boolean failNext;
    long delayMillis = 350;
    String respond(String rawRequest, String rawSession) throws JSONException {
        if (failNext) { failNext = false; throw new IllegalStateException("Demo request failed. Retry your answer."); }
        ItineraryJson.preflight(rawRequest, 5000);
        JSONObject request = new JSONObject(rawRequest);
        PlanningSession s = PlanningJson.parse(rawSession);
        PlanningJson.integer(request, "schemaVersion", 1, 1);
        if (!s.id.equals(PlanningJson.string(request, "sessionId", 100))
                || s.revision != PlanningJson.integer(request, "baseRevision", 0, Integer.MAX_VALUE)
                || !PlanningSession.IDS[s.current].equals(PlanningJson.string(request, "stepId", 30))
                || !s.step().question.id.equals(PlanningJson.string(request, "questionId", 150)))
            throw new IllegalArgumentException("Outdated question; retry");
        int index = s.current;
        String action = PlanningJson.choice(request, "action", "REFINE", "CONFIRM", "SUBMIT");
        JSONObject input = request.getJSONObject("input");
        String type = PlanningJson.choice(input, "type", "TEXT", "OPTIONS", "DATE_RANGE");
        List<String> keywords = new ArrayList<>();
        if (action.equals("REFINE")) {
            if (!type.equals("TEXT")) throw new IllegalArgumentException("Refine needs text");
            s.refine(PlanningSession.IDS[index], PlanningJson.string(input, "text", 500));
        } else {
            String answer;
            if (type.equals("TEXT")) {
                if (index < 3) throw new IllegalArgumentException("Confirm a refreshed suggestion first");
                answer = InputValidation.requiredText(input.opt("text"), 500, "Answer");
                for (String word : answer.split("[\\s,;]+")) if (keywords.size() < 8 && !word.isEmpty()) keywords.add(word);
            } else if (type.equals("DATE_RANGE")) {
                if (index != 2) throw new IllegalArgumentException("Date range is only valid for dates");
                int start = PlanningJson.integer(input, "startDate", 20000101, 99991231);
                int end = PlanningJson.integer(input, "endDate", start, 99991231);
                s.dateDraft = new TripPlanState(); s.selectDate(start); s.selectDate(end);
                if (!s.dateDraft.isStepComplete(2)) throw new IllegalArgumentException("Choose a range of at most 60 days");
                answer = TripDates.format(start) + " → " + TripDates.format(end); keywords.add("Fixed dates");
            } else {
                JSONArray ids = input.getJSONArray("optionIds");
                if (ids.length() < s.step().question.minSelections || ids.length() > s.step().question.maxSelections)
                    throw new IllegalArgumentException("Choose an option to continue");
                List<String> values = new ArrayList<>(); java.util.Set<String> seen = new java.util.HashSet<>();
                s.step().selected.clear();
                for (int i = 0; i < ids.length(); i++) {
                    String id = InputValidation.requiredText(ids.get(i), 150, "Option");
                    PlanningSession.Option found = null;
                    for (PlanningSession.Option o : s.step().question.options) if (o.id.equals(id)) found = o;
                    if (found == null || !seen.add(id)) throw new IllegalArgumentException("This option has changed; choose again");
                    s.step().selected.add(id); values.add(found.value); keywords.addAll(found.keywords);
                }
                if (ids.length() > 1 && (seen.contains("solo") || seen.contains("none"))) throw new IllegalArgumentException("Exclusive selection");
                answer = String.join(" · ", values);
                if (index == 2) s.dateDraft.useFlexibleDates();
            }
            s.accept(PlanningSession.IDS[index], answer, keywords);
            if (index == 1) { s.steps[4].question = null; s.rebuildQuestions(); }
        }
        String navigation = action.equals("REFINE") ? "STAY" : s.canGenerate() ? "GENERATE" : "NEXT";
        JSONObject result = new JSONObject().put("stepId", PlanningSession.IDS[index])
                .put("status", action.equals("REFINE") ? "UPDATED" : "ACCEPTED").put("keywords", new JSONArray(keywords))
                .put("navigation", navigation).put("nextStepId", navigation.equals("NEXT") ? PlanningSession.IDS[s.current] : JSONObject.NULL);
        return new JSONObject().put("schemaVersion", 1).put("requestId", PlanningJson.string(request, "requestId", 100))
                .put("result", result).put("session", PlanningJson.snapshot(s)).toString();
    }
}
