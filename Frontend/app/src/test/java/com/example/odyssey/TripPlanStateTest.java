package com.example.odyssey;

import static org.junit.Assert.*;
import java.io.*;
import org.junit.Test;

public class TripPlanStateTest {
    private TripPlanState locationComplete() {
        TripPlanState state = new TripPlanState();
        state.selectDeparture("Sydney");
        state.selectDestination("Kyoto");
        return state;
    }

    @Test public void locationNeedsTwoDifferentCitiesAndExplicitNext() {
        TripPlanState state = new TripPlanState();
        state.selectDestination("Kyoto");
        assertEquals(1, state.getCurrentStep());
        assertFalse(state.canGoNext());
        state.selectDeparture(" kyoto ");
        assertFalse(state.canGoNext());
        state.selectDeparture("Sydney");
        assertEquals(1, state.getCurrentStep());
        assertTrue(state.canGoNext());
        state.goNext();
        assertEquals(2, state.getCurrentStep());
        assertFalse(state.canGoNext());
    }

    @Test public void earlierSecondTapBecomesNewDeparture() {
        TripPlanState state = locationComplete();
        state.goNext();
        state.selectDate(20261220);
        state.selectDate(20261210);
        assertEquals(20261210, state.getStartDate());
        assertEquals(0, state.getEndDate());
        assertFalse(state.canGoNext());
        state.selectDate(20261225);
        assertTrue(state.canGoNext());
    }

    @Test public void sameDayAndCrossMonthRangesAreValidAndThirdTapRestarts() {
        TripPlanState state = locationComplete();
        state.selectDate(20261231);
        state.selectDate(20270102);
        assertEquals(3, state.getDurationDays());
        state.selectDate(20270110);
        assertEquals(0, state.getEndDate());
        state.selectDate(20270110);
        assertEquals(1, state.getDurationDays());
    }

    @Test public void flexibleDatesClearRangeAndSelectingDayClearsFlexible() {
        TripPlanState state = locationComplete();
        state.goNext();
        state.selectDate(20261201);
        state.useFlexibleDates();
        assertEquals(0, state.getStartDate());
        assertTrue(state.canGoNext());
        state.selectDate(20261203);
        assertFalse(state.isFlexibleDates());
        assertFalse(state.canGoNext());
    }

    @Test public void fullFlowRequiresCompanionAndBudgetThenGenerates() {
        TripPlanState state = locationComplete();
        state.goBack();
        assertEquals(1, state.getCurrentStep());
        state.goNext();
        state.useFlexibleDates();
        state.goNext();
        assertFalse(state.canGoNext());
        state.setCompanion("Friends");
        state.goNext();
        assertEquals(4, state.getCurrentStep());
        assertFalse(state.canGoNext());
        state.setBudget("Comfort");
        state.goNext();
        assertEquals(5, state.getCurrentStep());
        assertTrue(state.canGoNext()); // No restrictions is valid.
        state.goNext();
        assertTrue(state.isGenerating());
        assertFalse(state.canGoNext());
        state.finishGeneration();
        assertTrue(state.isGenerated());
        state.goBack();
        assertEquals(5, state.getCurrentStep());
        assertFalse(state.isGenerated());
        assertEquals("Friends", state.getCompanion());
    }

    @Test public void changedDestinationInvalidatesItsBudgetOnly() {
        TripPlanState state = locationComplete();
        state.setBudget("Comfort");
        state.setCompanion("Solo");
        state.selectDestination("Kyoto");
        assertEquals("Comfort", state.getBudget());
        state.selectDestination("Tokyo");
        assertEquals("", state.getBudget());
        assertEquals("Solo", state.getCompanion());
    }

    @Test public void constraintsToggleIndependentlyAndTextIsPreserved() {
        TripPlanState state = locationComplete();
        state.toggleConstraint("Vegetarian");
        state.toggleConstraint("With pets");
        state.toggleConstraint("Vegetarian");
        state.setNote(5, "  Avoid stairs  ");
        assertEquals(1, state.getConstraints().size());
        assertTrue(state.getConstraints().contains("With pets"));
        assertEquals("Avoid stairs", state.getNote(5));
    }

    @Test public void stateSurvivesSerializationIncludingDraftAndGeneration() throws Exception {
        TripPlanState state = locationComplete();
        state.goNext();
        state.useFlexibleDates();
        state.goNext();
        state.setCompanion("Family");
        state.goNext();
        state.setBudget("Comfort");
        state.goNext();
        state.toggleConstraint("Less walking");
        state.setDraft(5, "Wheelchair");
        state.goNext();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(state);
        TripPlanState restored = (TripPlanState) new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray())).readObject();
        assertTrue(restored.isGenerating());
        assertEquals("Sydney", restored.getDeparture());
        assertEquals("Kyoto", restored.getDestination());
        assertEquals("Wheelchair", restored.getDraft(5));
        assertTrue(restored.getConstraints().contains("Less walking"));
        restored.goBack();
        restored.finishGeneration(); // A cancelled mock callback must not complete.
        assertFalse(restored.isGenerated());
    }

    @Test public void leapYearDurationUsesCalendarDays() {
        assertEquals(3, TripDates.daysBetweenInclusive(20280228, 20280301));
        assertEquals(2, TripDates.daysBetweenInclusive(20270228, 20270301));
    }
}
