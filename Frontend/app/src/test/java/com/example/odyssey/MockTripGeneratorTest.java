package com.example.odyssey;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.List;

public class MockTripGeneratorTest {
    @Test public void catalogHasRequiredShapesAndDestinationDependentBudgets() {
        for (TripOptions.Destination destination : TripOptions.DESTINATIONS) {
            assertEquals(3, destination.keywords.length);
            assertEquals(3, TripOptions.budgets(destination.city).length);
        }
        assertEquals(4, TripOptions.COMPANIONS.length);
        assertEquals(6, TripOptions.CONSTRAINTS.length);
        assertNotEquals(TripOptions.budgets("Tokyo")[0].description,
                TripOptions.budgets("Bali")[0].description);
    }

    @Test public void mockPreviewReflectsCityDurationAndDietWithoutInventingFullLongTrip() {
        TripPlanState state = new TripPlanState();
        state.selectDeparture("Sydney");
        state.selectDestination("Kyoto");
        state.selectDate(20261201);
        state.selectDate(20261210);
        state.setCompanion("Family");
        state.setBudget("Comfort");
        state.toggleConstraint("Vegetarian");
        state.toggleConstraint("Less walking");
        List<MockTripGenerator.Day> days = MockTripGenerator.generate(state);
        assertEquals(3, days.size());
        assertTrue(days.get(0).description.contains("Kyoto"));
        assertTrue(days.get(0).description.contains("vegetarian"));
        assertTrue(days.get(0).description.contains("short transfers"));
        assertTrue(days.get(0).description.contains("Family"));
        state.selectDate(20261220);
        state.selectDate(20261220);
        assertEquals(1, MockTripGenerator.generate(state).size());
    }
}
