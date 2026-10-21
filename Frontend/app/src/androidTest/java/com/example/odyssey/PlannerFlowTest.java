package com.example.odyssey;

import static org.junit.Assert.*;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Calendar;
import java.util.TimeZone;

@RunWith(AndroidJUnit4.class)
public class PlannerFlowTest {
    @Test public void restoredCalendarMonthDoesNotShiftInWesternTimezone() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                TimeZone original = TimeZone.getDefault();
                try {
                    TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
                    TripCalendarView calendar = new TripCalendarView(activity, new TripPlanState(),
                            TripDates.calendar(20270101), date -> {}, () -> {});
                    assertNotNull(findText(calendar, "January 2027"));
                } finally {
                    TimeZone.setDefault(original);
                }
            });
        }
    }

    @Test public void fiveStepsReachMockResultAndCanEditWithoutLosingAnswers() throws Exception {
        android.content.Context context = androidx.test.platform.app.InstrumentationRegistry
                .getInstrumentation().getTargetContext();
        int savedBefore = new TripStore(context).list().size();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(View.GONE, activity.findViewById(R.id.tripBoard).getVisibility());
                clickText(activity, "Sydney");
                assertEquals("Depart from Sydney", value(activity, R.id.tripBoardDestination));
                clickText(activity, "Kyoto");
                assertEquals("Sydney → Kyoto", value(activity, R.id.tripBoardDestination));
                click(activity, R.id.nextButton);
                assertEquals("02 / 05", value(activity, R.id.stepText));
                clickText(activity, "Let us suggest dates");
                click(activity, R.id.nextButton);
                clickText(activity, "Friends");
                click(activity, R.id.nextButton);
                clickText(activity, "Comfort");
                click(activity, R.id.nextButton);
                clickText(activity, "Vegetarian");
                clickText(activity, "Less walking");
                ((TextView) activity.findViewById(R.id.messageInput)).setText("Avoid stairs");
                click(activity, R.id.sendButton);
                clickText(activity, "Generate demo trip");
                assertEquals("DEMO", value(activity, R.id.stepText));
                assertEquals(View.GONE, activity.findViewById(R.id.composerContainer).getVisibility());
            });
            // 1.2 秒只是 Handler 的最早执行时间，之后还有后台落盘和 UI 回调。
            // 慢速模拟器不能用固定 sleep 猜完成；等待真实成功状态，超时仍明确失败。
            long deadline = SystemClock.uptimeMillis() + 10000;
            java.util.concurrent.atomic.AtomicBoolean saved = new java.util.concurrent.atomic.AtomicBoolean(false);
            do {
                scenario.onActivity(activity -> {
                    TextView status = activity.findViewById(R.id.saveTripStatus);
                    saved.set(status != null && "Saved to My Trips · On this device".contentEquals(status.getText()));
                });
                if (saved.get()) break;
                SystemClock.sleep(50);
            } while (SystemClock.uptimeMillis() < deadline);
            assertTrue("Generation must finish and persist within ten seconds", saved.get());
            assertEquals(savedBefore + 1, new TripStore(context).list().size());
            assertEquals("Sydney → Kyoto", new TripStore(context).list().get(0).route);
            assertTrue(new TripStore(context).list().get(0).brief.contains("Avoid stairs"));
            scenario.recreate();
            assertEquals(savedBefore + 1, new TripStore(context).list().size());
            scenario.onActivity(activity -> {
                assertEquals("Your trip, taking shape.", value(activity, R.id.planHeadline));
                assertEquals("Saved to My Trips · On this device", value(activity, R.id.saveTripStatus));
                assertNotNull(activity.findViewById(R.id.openItinerary));
                assertNotNull(findText(activity.findViewById(R.id.stepContent), "Preferences note: Avoid stairs"));
                clickText(activity, "Edit my choices");
                assertEquals("05 / 05", value(activity, R.id.stepText));
                assertNotNull(findText(activity.findViewById(R.id.stepContent), "✓ Vegetarian"));
                click(activity, R.id.backButton);
                assertEquals("04 / 05", value(activity, R.id.stepText));
                assertNotNull(findText(activity.findViewById(R.id.stepContent), "✓ Comfort"));
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals("04 / 05", value(activity, R.id.stepText));
                assertNotNull(findText(activity.findViewById(R.id.stepContent), "✓ Comfort"));
            });
        }
    }

    @Test public void calendarEarlierSecondTapRestartsAndDraftSurvivesRecreation() {
        Calendar nextMonth = Calendar.getInstance();
        nextMonth.set(Calendar.DAY_OF_MONTH, 1);
        nextMonth.add(Calendar.MONTH, 1);
        int base = TripDates.key(nextMonth) - 1;
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                clickText(activity, "Sydney");
                clickText(activity, "Tokyo");
                click(activity, R.id.nextButton);
                View monthButton = findDescription(activity.findViewById(R.id.main), "Next month");
                assertNotNull(monthButton);
                monthButton.performClick();
                clickDate(activity, base + 20);
                clickDate(activity, base + 10);
                assertFalse(activity.findViewById(R.id.nextButton).isEnabled());
                assertEquals("Departure changed. Now choose your return date.", value(activity, R.id.inputFeedback));
                clickDate(activity, base + 25);
                assertTrue(activity.findViewById(R.id.nextButton).isEnabled());
                ((TextView) activity.findViewById(R.id.messageInput)).setText("Prefer autumn");
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals("02 / 05", value(activity, R.id.stepText));
                assertEquals("Prefer autumn", value(activity, R.id.messageInput));
                assertTrue(activity.findViewById(R.id.nextButton).isEnabled());
                View date = activity.findViewById(R.id.stepContent).findViewWithTag("date-" + (base + 10));
                assertNotNull(date);
                assertTrue(date.isSelected());
            });
        }
    }

    @Test public void customAnswersAndDarkThemeRemainUsable() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                send(activity, "Brisbane");
                assertEquals("Depart from Brisbane", value(activity, R.id.tripBoardDestination));
                send(activity, "Brisbane");
                assertFalse(activity.findViewById(R.id.nextButton).isEnabled());
                send(activity, "Seoul");
                assertTrue(activity.findViewById(R.id.nextButton).isEnabled());
                click(activity, R.id.nextButton);
                assertEquals("02 / 05", value(activity, R.id.stepText));
                clickText(activity, "Let us suggest dates");
                click(activity, R.id.nextButton);
                send(activity, "Three colleagues");
                click(activity, R.id.nextButton);
                send(activity, "AUD 250 per day");
                click(activity, R.id.nextButton);
                activity.getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            });
            scenario.onActivity(activity -> {
                assertEquals("05 / 05", value(activity, R.id.stepText));
                assertEquals("Brisbane → Seoul", value(activity, R.id.tripBoardDestination));
                assertTrue(activity.findViewById(R.id.nextButton).isEnabled());
            });
        }
    }

    private static void send(MainActivity activity, String answer) {
        ((TextView) activity.findViewById(R.id.messageInput)).setText(answer);
        click(activity, R.id.sendButton);
    }
    private static void click(MainActivity activity, int id) { activity.findViewById(id).performClick(); }
    private static String value(MainActivity activity, int id) {
        return ((TextView) activity.findViewById(id)).getText().toString();
    }
    private static void clickDate(MainActivity activity, int key) {
        View date = activity.findViewById(R.id.stepContent).findViewWithTag("date-" + key);
        assertNotNull(date);
        date.performClick();
    }
    private static void clickText(MainActivity activity, String text) {
        View found = findText(activity.findViewById(R.id.stepContent), text);
        assertNotNull("Missing option: " + text, found);
        while (!found.isClickable() && found.getParent() instanceof View) found = (View) found.getParent();
        assertTrue("Not clickable: " + text, found.performClick());
    }
    private static View findText(View view, String text) {
        if (view instanceof TextView && ((TextView) view).getText().toString().equals(text)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
    private static View findDescription(View view, String text) {
        if (text.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findDescription(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
