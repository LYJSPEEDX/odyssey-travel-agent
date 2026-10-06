package com.example.odyssey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TripPlanStateTest {

    @Test
    public void selectingDestinationAdvancesToStepTwo() {
        TripPlanState state = new TripPlanState();

        state.selectDestination("Kyoto");

        assertEquals(2, state.getCurrentStep());
        assertEquals("Kyoto", state.getDestination());
    }

    @Test
    public void changingDestinationKeepsStepTwo() {
        TripPlanState state = new TripPlanState();
        state.selectDestination("Kyoto");

        state.selectDestination("Tokyo");

        assertEquals(2, state.getCurrentStep());
        assertEquals("Tokyo", state.getDestination());
    }

    @Test
    public void nextAndBackMoveBetweenSteps() {
        TripPlanState state = new TripPlanState();
        state.selectDestination("Kyoto");

        state.goNext();
        assertEquals(3, state.getCurrentStep());

        state.goBack();
        assertEquals(2, state.getCurrentStep());
    }

    @Test
    public void navigationStopsAtFirstAndLastStep() {
        TripPlanState state = new TripPlanState();
        assertFalse(state.canGoBack());
        assertFalse(state.canGoNext());

        state.goBack();
        state.goNext();
        assertEquals(1, state.getCurrentStep());

        state.selectDestination("Kyoto");
        assertTrue(state.canGoBack());
        assertTrue(state.canGoNext());
        state.goNext();
        state.goNext();
        state.goNext();
        state.goNext();
        assertEquals(5, state.getCurrentStep());
        assertFalse(state.canGoNext());
    }
}
