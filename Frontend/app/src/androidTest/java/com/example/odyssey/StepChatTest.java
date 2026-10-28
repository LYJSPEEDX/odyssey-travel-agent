package com.example.odyssey;

import static org.junit.Assert.*;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import org.junit.Test;

/** 验证选项仍在会话上方，且回到某一步时只显示该步的聊天记录。 */
public class StepChatTest {
    @Test public void laterStepChatNeedsExplicitConfirmation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            PlannerFlowTest.choose(scenario, "Sydney", "1/7");
            PlannerFlowTest.choose(scenario, "Kyoto", "2/7");
            PlannerFlowTest.choose(scenario, "Flexible dates", "3/7");
            scenario.onActivity(a -> PlannerFlowTest.send(a, "Two friends"));
            PlannerFlowTest.await(scenario, a -> PlannerFlowTest.findText(a.findViewById(R.id.stepContent), "Use as answer") != null);
            scenario.onActivity(a -> {
                assertEquals("3/7", PlannerFlowTest.value(a, R.id.stepText));
                assertNotNull(PlannerFlowTest.findText(a.findViewById(R.id.stepContent), "You · Two friends"));
                PlannerFlowTest.clickText(a, "Use as answer");
            });
            PlannerFlowTest.awaitStep(scenario, "4/7");
        }
    }
    @Test public void refinementChatSurvivesRoundTripAndRecreation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> PlannerFlowTest.send(a, "Perth"));
            PlannerFlowTest.await(scenario, a -> PlannerFlowTest.findText(a.findViewById(R.id.stepContent), "You · Perth") != null);
            scenario.onActivity(a -> {
                ViewGroup container = a.findViewById(R.id.stepContent);
                assertNotNull(PlannerFlowTest.findText(container, "You · Perth"));
                assertNotNull(PlannerFlowTest.findText(container, "AI demo · Demo reply: I refreshed the suggestions above. Please choose and confirm."));
                PlannerFlowTest.clickText(a, "Perth"); PlannerFlowTest.clickText(a, "Confirm departure");
            });
            PlannerFlowTest.awaitStep(scenario, "1/7");
            scenario.onActivity(a -> {
                assertNull(PlannerFlowTest.findText(a.findViewById(R.id.stepContent), "You · Perth"));
                a.findViewById(R.id.backButton).performClick();
            });
            scenario.recreate();
            scenario.onActivity(a -> assertNotNull(PlannerFlowTest.findText(a.findViewById(R.id.stepContent), "You · Perth")));
        }
    }
}
