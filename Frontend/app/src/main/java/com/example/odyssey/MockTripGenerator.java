package com.example.odyssey;

import java.util.ArrayList;
import java.util.List;

/** Deterministic sample content, not an AI response or a bookable itinerary. */
final class MockTripGenerator {
    static final class Day {
        final String title;
        final String description;
        Day(String title, String description) { this.title = title; this.description = description; }
    }

    static List<Day> generate(TripPlanState state) {
        int days = state.isFlexibleDates() ? 3 : state.getDurationDays();
        int previewDays = Math.min(days, 3);
        boolean easy = state.getConstraints().contains("Take it slow")
                || state.getConstraints().contains("Less walking")
                || state.getConstraints().contains("With older adults");
        boolean more = state.getConstraints().contains("See more") && !easy;
        List<Day> result = new ArrayList<>();
        String[] themes = {"Arrive & settle in", "A taste of local life", "One last discovery"};
        for (int day = 0; day < previewDays; day++) {
            String activity = day == 0 ? "Explore a neighbourhood in " + state.getDestination()
                    : day == 1 ? "Visit a local gallery or market" : "Enjoy a park and a café";
            String text = "Morning · " + activity + ".\n"
                    + "Lunch · " + (state.getConstraints().contains("Vegetarian")
                    ? "Find a restaurant with vegetarian choices." : "Try a local food spot.")
                    + "\nAfternoon · " + (easy ? "One nearby stop, with plenty of rest."
                    : more ? "Two more neighbourhood stops and a local market."
                    : "One relaxed cultural stop, then free time.");
            if (state.getConstraints().contains("Less walking")) text += "\nTransport · Prefer short transfers.";
            if (state.getConstraints().contains("With pets")) text += "\nPets · Check each venue’s pet policy before visiting.";
            if (state.getConstraints().contains("With older adults")) text += "\nAccess · Confirm step-free access and seating.";
            text += "\nCrew · " + state.getCompanion() + " · Budget style · " + state.getBudget();
            result.add(new Day(themes[day], text));
        }
        return result;
    }
    private MockTripGenerator() {}
}
