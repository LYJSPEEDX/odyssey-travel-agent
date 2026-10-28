package com.example.odyssey;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlanningSessionTest {
    @Test public void withheldLocationDoesNotBecomeAnInventedHomeCity() {
        PlanningSession session = new PlanningSession("", "");
        assertEquals("", session.residence);
        assertTrue(session.step().question.options.stream().noneMatch(option -> "From your profile".equals(option.description)));
        assertTrue(session.step().question.options.stream().anyMatch(option -> "Sydney".equals(option.title)));
    }
    @Test public void fixedSlotsAndRefineStayUntilConfirmation() {
        PlanningSession session = new PlanningSession("Melbourne", "Museums");
        assertEquals(8, session.steps.length);
        assertEquals("START", session.progress());
        session.refine("ORIGIN", "Perth");
        assertEquals(0, session.current);
        assertEquals("Perth", session.step().question.options.get(0).value);
        session.accept("ORIGIN", "Perth", java.util.Collections.singletonList("Perth"));
        assertEquals("1/7", session.progress());
    }
    @Test public void editingRetainsButStalesDownstreamAnswers() {
        PlanningSession session = new PlanningSession("Sydney", "Food");
        session.accept("ORIGIN", "Sydney", java.util.Collections.singletonList("Sydney"));
        session.accept("DESTINATION", "Tokyo", java.util.Collections.singletonList("Tokyo"));
        session.current = 0;
        session.accept("ORIGIN", "Perth", java.util.Collections.singletonList("Perth"));
        assertEquals("Tokyo", session.steps[1].answer);
        assertEquals("NEEDS_REVIEW", session.steps[1].state);
        assertFalse(session.canGenerate());
    }
    @Test public void soloIsExclusiveAndEarlierDateRestarts() {
        PlanningSession session = new PlanningSession("Sydney", "");
        session.current = 3;
        session.toggle("friends"); session.toggle("family");
        assertEquals(2, session.step().selected.size());
        session.toggle("solo");
        assertEquals(java.util.Collections.singleton("solo"), session.step().selected);
        session.toggle("partner");
        assertFalse(session.step().selected.contains("solo"));
        session.selectDate(20261112); session.selectDate(20261110);
        assertEquals(20261110, session.dateDraft.getStartDate());
        assertEquals(0, session.dateDraft.getEndDate());
    }
}
