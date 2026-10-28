package com.example.odyssey;

import static org.junit.Assert.*;
import androidx.test.core.app.ActivityScenario;
import org.json.JSONObject;
import org.junit.Test;

/** 不确定输入留在当前步骤，回复可见，而且不能把它当作答案。 */
public class PlanningClarificationTest {
    @Test public void unknownCityDoesNotBecomeAnOption() throws Exception {
        PlanningSession session = new PlanningSession("", "");
        JSONObject request = PlanningJson.request(session, "REFINE", PlanningJson.textInput("asdfghjkl"));
        String response = new MockPlanningService().respond(request.toString(), PlanningJson.snapshot(session).toString());
        assertEquals("CLARIFY", new JSONObject(response).getJSONObject("result").getString("status"));
        PlanningJson.Applied applied = PlanningJson.applyResult(session, request, response);
        assertEquals("STAY", applied.navigation);
        assertEquals(0, applied.session.current);
        assertTrue(applied.session.step().selected.isEmpty());
        assertTrue(applied.session.step().question.options.stream().noneMatch(o -> o.value.equals("asdfghjkl")));
        assertTrue(applied.session.step().messages.get(1).text.contains("cannot verify"));
    }
    @Test public void unclearBudgetDoesNotBecomeAChatAnswer() throws Exception {
        PlanningSession session = new PlanningSession("", "");
        for (int i = 0; i < 4; i++) { session.steps[i].state = "CONFIRMED"; session.steps[i].answer = "Example"; }
        session.current = 4; session.step().state = "AWAITING_INPUT";
        JSONObject request = PlanningJson.request(session, "CHAT", PlanningJson.textInput("500"));
        String response = new MockPlanningService().respond(request.toString(), PlanningJson.snapshot(session).toString());
        PlanningJson.Applied applied = PlanningJson.applyResult(session, request, response);
        assertEquals("STAY", applied.navigation);
        assertEquals("", applied.session.step().chatCandidate);
        assertTrue(applied.session.step().messages.get(1).text.contains("currency"));
    }
    @Test public void clarificationIsVisibleOnScreen() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> PlannerFlowTest.send(a, "asdfghjkl"));
            PlannerFlowTest.await(scenario, a -> PlannerFlowTest.findText(a.findViewById(R.id.stepContent),
                    "AI demo · Demo: I cannot verify this place yet. Try an example city such as Perth or Sydney.") != null);
            scenario.onActivity(a -> assertEquals("START", PlannerFlowTest.value(a, R.id.stepText)));
        }
    }
}
