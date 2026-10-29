package com.example.odyssey;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.JSONObject;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MockPlanningServiceTest {
    @Test public void refineAndConfirmationCrossJsonBoundary() throws Exception {
        PlanningSession s = new PlanningSession("Sydney", "Food");
        JSONObject request = PlanningJson.request(s, "REFINE", PlanningJson.textInput("Perth"));
        String response = new MockPlanningService().respond(request.toString(), PlanningJson.snapshot(s).toString());
        PlanningSession updated = PlanningJson.apply(s, request, response);
        assertEquals(0, updated.current);
        assertEquals("Perth", updated.step().question.options.get(0).value);
        JSONObject confirm = PlanningJson.request(updated, "CONFIRM", PlanningJson.optionsInput(java.util.Collections.singleton("custom")));
        updated = PlanningJson.apply(updated, confirm, new MockPlanningService().respond(confirm.toString(), PlanningJson.snapshot(updated).toString()));
        assertEquals(1, updated.current);
        assertEquals("Perth", updated.answer(0));
    }
    @Test public void rejectsRacedAndMalformedResponsesAndObsoleteOptions() throws Exception {
        PlanningSession s = new PlanningSession("Sydney", "");
        JSONObject request = PlanningJson.request(s, "CONFIRM", PlanningJson.optionsInput(java.util.Collections.singleton("home")));
        String response = new MockPlanningService().respond(request.toString(), PlanningJson.snapshot(s).toString());
        s.revision++;
        try { PlanningJson.apply(s, request, response); fail("stale response accepted"); }
        catch (IllegalArgumentException expected) { }
        s.revision--;
        JSONObject malformed = new JSONObject(response);
        malformed.getJSONObject("session").put("revision", "1");
        try { PlanningJson.apply(s, request, malformed.toString()); fail("wrong type accepted"); }
        catch (IllegalArgumentException expected) { }
        request.getJSONObject("input").put("optionIds", new org.json.JSONArray().put("obsolete"));
        try { new MockPlanningService().respond(request.toString(), PlanningJson.snapshot(s).toString()); fail(); }
        catch (IllegalArgumentException expected) { }
    }
    @Test public void deterministicFailureKeepsOriginalDraft() throws Exception {
        PlanningSession s = new PlanningSession("Sydney", ""); s.step().draft = "Perth";
        MockPlanningService service = new MockPlanningService(); service.failNext = true;
        try { service.respond(PlanningJson.request(s, "REFINE", PlanningJson.textInput("Perth")).toString(), PlanningJson.snapshot(s).toString()); fail(); }
        catch (IllegalStateException expected) { }
        assertEquals("Perth", s.step().draft);
    }
    @Test public void fullSessionRebuildsBudgetAndRejectsOldFollowupIds() throws Exception {
        PlanningSession s = new PlanningSession("Sydney", "Food");
        s = confirm(s, "home"); s = confirm(s, "tokyo");
        assertTrue(s.steps[4].question.options.get(0).description.contains("AUD 130"));
        s = dates(s); s = confirm(s, "friends", "family"); s = confirm(s, "budget-1");
        s = confirm(s, "constraint-0", "constraint-3");
        String oldId = s.step().question.options.get(0).id;
        s = confirm(s, oldId); s = confirm(s, s.step().question.options.get(0).id);
        assertTrue(s.canGenerate());
        TripPlanState plan = s.toPlan();
        assertTrue(plan.isGenerating()); assertEquals(2, plan.getConstraints().size());
        assertEquals("Friends · Family", plan.getCompanion());
        s.current = 1; s = confirm(s, "bali");
        assertEquals("NEEDS_REVIEW", s.steps[6].state);
        assertFalse(s.steps[6].question.options.get(0).id.equals(oldId));
        assertTrue(s.steps[4].question.options.get(0).description.contains("AUD 70"));
    }
    private static PlanningSession dates(PlanningSession s) throws Exception {
        s.selectDate(20261210); s.selectDate(20261214);
        JSONObject request = PlanningJson.request(s, "CONFIRM", PlanningJson.dateInput(s.dateDraft));
        return PlanningJson.apply(s, request, new MockPlanningService().respond(request.toString(), PlanningJson.snapshot(s).toString()));
    }
    private static PlanningSession confirm(PlanningSession s, String... ids) throws Exception {
        JSONObject request = PlanningJson.request(s, "CONFIRM", PlanningJson.optionsInput(java.util.Arrays.asList(ids)));
        return PlanningJson.apply(s, request, new MockPlanningService().respond(request.toString(), PlanningJson.snapshot(s).toString()));
    }
    @Test public void textAnswerReplacesPreviousOptionSelection() throws Exception {
        PlanningSession s = new PlanningSession("Sydney", "");
        s = confirm(s, "home"); s = confirm(s, "tokyo"); s = dates(s); s = confirm(s, "friends");
        s.current = 3;
        JSONObject request = PlanningJson.request(s, "SUBMIT", PlanningJson.textInput("Three colleagues"));
        s = PlanningJson.apply(s, request, new MockPlanningService().respond(request.toString(), PlanningJson.snapshot(s).toString()));
        assertEquals("Three colleagues", s.answer(3));
        assertTrue("Text replacement must not keep an unrelated checked card", s.steps[3].selected.isEmpty());
        assertEquals(java.util.Arrays.asList("Three", "colleagues"), s.steps[3].keywords);
    }
    @Test public void sameEarlierAnswerInCompleteSessionNavigatesWithoutGenerating() throws Exception {
        PlanningSession s = new PlanningSession("Sydney", "");
        s = confirm(s, "home"); s = confirm(s, "tokyo"); s = dates(s);
        s = confirm(s, "solo"); s = confirm(s, "budget-1"); s = confirm(s, "none");
        s = confirm(s, s.step().question.options.get(0).id); s = confirm(s, s.step().question.options.get(0).id);
        assertTrue(s.canGenerate()); s.current = 0;
        JSONObject request = PlanningJson.request(s, "CONFIRM", PlanningJson.optionsInput(java.util.Collections.singleton("home")));
        String response = new MockPlanningService().respond(request.toString(), PlanningJson.snapshot(s).toString());
        assertEquals("NEXT", new JSONObject(response).getJSONObject("result").getString("navigation"));
        s = PlanningJson.apply(s, request, response);
        assertEquals(1, s.current); assertTrue(s.canGenerate());
    }
}
