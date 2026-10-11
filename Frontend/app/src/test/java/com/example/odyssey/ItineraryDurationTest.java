package com.example.odyssey;

import org.junit.Test;
import static org.junit.Assert.*;

public class ItineraryDurationTest {
    @Test public void contractLimitIsShownBeforeGeneration() {
        TripPlanState state = new TripPlanState();
        state.selectDate(20261101); state.selectDate(20261230);
        assertEquals(60, state.getDurationDays());
        assertTrue(state.isStepComplete(2));
        state.selectDate(20261101); state.selectDate(20261231);
        assertFalse(state.isStepComplete(2));
        state.useFlexibleDates();
        assertTrue(state.isStepComplete(2));
    }
}
