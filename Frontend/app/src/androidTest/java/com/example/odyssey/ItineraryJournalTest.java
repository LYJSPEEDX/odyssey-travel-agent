package com.example.odyssey;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.withTagValue;
import static org.hamcrest.Matchers.is;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ItineraryJournalTest {
    @Test public void dayNotesRemainAccessible() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = TestTrips.saved(context, TestTrips.NEW_YORK);
        try (ActivityScenario<TripDetailActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                View day = activity.findViewById(R.id.tripDetailContent).findViewWithTag("contract-day-0");
                assertTrue(day.getContentDescription().toString().contains("10 Nov"));
            });
            onView(withTagValue(is("journal-day-notes"))).perform(scrollTo(), click());
            onView(withText(org.hamcrest.Matchers.containsString("America/New_York")))
                    .check(matches(withText(org.hamcrest.Matchers.containsString("A flexible day"))));
        }
    }
    @Test public void journalUsesCompactHeaderTimelineAndHotelRail() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = TestTrips.saved(context, TestTrips.NEW_YORK);
        try (ActivityScenario<TripDetailActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                View content = activity.findViewById(R.id.tripDetailContent);
                assertNotNull("Compact journal header", content.findViewWithTag("journal-header"));
                assertNotNull("Timeline connects events", content.findViewWithTag("timeline-row"));
                assertNotNull("Hotels compare horizontally", content.findViewWithTag("hotel-carousel"));
                assertFalse(ItineraryIntegrationTest.contains(content, "Photo preview"));
                assertFalse(ItineraryIntegrationTest.contains(content, "2026-11-10 06:00:00+11:00"));
            });
        }
    }
}
