package com.example.odyssey;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

/** 每次等待真实槽位/保存状态，而不是假设点击会同步完成 Mock。 */
@RunWith(AndroidJUnit4.class)
public class PlannerFlowTest {
    @org.junit.Rule public final ProfileTestFixture profile = new ProfileTestFixture();
    @Test public void sevenRoundsSaveCompanionsAndFollowupsWithoutDuplicates() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences library = context.getSharedPreferences(TripStore.FILE, Context.MODE_PRIVATE);
        java.util.Map<String, ?> previous = library.getAll();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            installPreview(scenario);
            choose(scenario, "Sydney", "1/7"); choose(scenario, "Kyoto", "2/7");
            chooseDates(scenario);
            scenario.onActivity(a -> { clickText(a, "Friends"); clickText(a, "Family"); clickText(a, "Continue"); });
            awaitStep(scenario, "4/7"); choose(scenario, "Comfort", "5/7");
            scenario.onActivity(a -> { clickText(a, "Vegetarian"); clickText(a, "Less walking"); clickText(a, "Continue"); });
            awaitStep(scenario, "6/7"); choose(scenario, "Relaxed", "7/7"); choose(scenario, "Local discoveries", "AI PREVIEW");
            await(scenario, a -> a.findViewById(R.id.saveTripStatus) instanceof TextView
                    && value(a, R.id.saveTripStatus).equals("Saved to My Trips · On this device"));
            SavedTrip saved = new TripStore(context).list().get(0);
            assertEquals("Sydney → Kyoto", saved.route);
            String savedContract = saved.contractJson;
            assertTrue(savedContract.contains("Friends · Family"));
            assertTrue(savedContract.contains("Relaxed pace")); assertTrue(savedContract.contains("Local discoveries"));
            assertTrue(savedContract.contains("Vegetarian"));
            int count = new TripStore(context).list().size(); scenario.recreate();
            assertEquals(count, new TripStore(context).list().size());
            scenario.onActivity(a -> { clickText(a, "Edit my choices"); assertEquals("7/7", value(a, R.id.stepText)); });
        } finally {
            SharedPreferences.Editor restore = library.edit().clear();
            for (java.util.Map.Entry<String, ?> entry : previous.entrySet()) restore.putString(entry.getKey(), (String) entry.getValue());
            assertTrue(restore.commit());
        }
    }
    @Test public void liveGenerationSurvivesRotationAndFailureCanRetry() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences library = context.getSharedPreferences(TripStore.FILE, Context.MODE_PRIVATE);
        java.util.Map<String, ?> previous = library.getAll();
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a -> new androidx.lifecycle.ViewModelProvider(a).get(TripGenerationModel.class).clientFactory = () -> new TripPreviewClient() {
                @Override String preview(org.json.JSONObject request) throws Exception {
                    int attempt = calls.incrementAndGet();
                    if (attempt == 1) throw new java.io.IOException("Synthetic upstream failure");
                    if (!release.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new java.io.IOException("Test timed out");
                    try (java.io.InputStream input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("backend-preview.json")) {
                        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
                        while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
                        return bytes.toString("UTF-8");
                    }
                }
            });
            choose(scenario, "Sydney", "1/7"); choose(scenario, "Tokyo", "2/7"); chooseDates(scenario);
            scenario.onActivity(a -> { clickText(a, "Solo"); clickText(a, "Continue"); }); awaitStep(scenario, "4/7");
            choose(scenario, "Comfort", "5/7");
            scenario.onActivity(a -> { clickText(a, "No restrictions"); clickText(a, "Continue"); }); awaitStep(scenario, "6/7");
            choose(scenario, "Balanced", "7/7"); choose(scenario, "Local discoveries", "AI PREVIEW");
            await(scenario, a -> findText(a.findViewById(R.id.stepContent), "Retry generation") != null);
            scenario.onActivity(a -> clickText(a, "Retry generation")); approveGeneration(scenario);
            await(scenario, a -> calls.get() == 2);
            scenario.recreate();
            scenario.onActivity(a -> assertTrue(new androidx.lifecycle.ViewModelProvider(a).get(TripGenerationModel.class).running()));
            assertEquals(2, calls.get()); release.countDown();
            await(scenario, a -> a.findViewById(R.id.saveTripStatus) != null && value(a, R.id.saveTripStatus).equals("Saved to My Trips · On this device"));
            scenario.recreate(); assertEquals(2, calls.get());
        } finally {
            release.countDown();
            SharedPreferences.Editor restore = library.edit().clear();
            for (java.util.Map.Entry<String, ?> entry : previous.entrySet()) restore.putString(entry.getKey(), (String) entry.getValue());
            assertTrue(restore.commit());
        }
    }
    @Test public void textRefinementStaysAndRotationCancelsSafely() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            installPreview(scenario);
            scenario.onActivity(a -> send(a, "Perth"));
            await(scenario, a -> findText(a.findViewById(R.id.stepContent), "Perth") != null);
            scenario.onActivity(a -> { assertEquals("START", value(a, R.id.stepText)); assertEquals(View.GONE, a.findViewById(R.id.tripBoard).getVisibility()); });
            choose(scenario, "Perth", "1/7");
            scenario.onActivity(a -> { mock(a).delayMillis = 10000; send(a, "Seoul"); });
            scenario.recreate();
            scenario.onActivity(a -> { assertEquals("1/7", value(a, R.id.stepText)); assertEquals("Seoul", value(a, R.id.messageInput)); assertTrue(a.findViewById(R.id.sendButton).isEnabled()); click(a, R.id.sendButton); });
            await(scenario, a -> findText(a.findViewById(R.id.stepContent), "Seoul") != null);
            scenario.onActivity(a -> assertEquals("1/7", value(a, R.id.stepText)));
            choose(scenario, "Seoul", "2/7");
        }
    }
    @Test public void backgroundCancellationRestoresInteractiveDraftOnResume() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            installPreview(scenario);
            scenario.onActivity(a -> {
                mock(a).delayMillis = 10000;
                send(a, "Perth");
            });
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
            scenario.onActivity(a -> {
                assertEquals("START", value(a, R.id.stepText));
                assertEquals("Perth", value(a, R.id.messageInput));
                assertTrue("Cancelled updates must restore the send action", a.findViewById(R.id.sendButton).isEnabled());
            });
        }
    }
    @Test public void injectedFailureShowsRetryAndRetainsDraft() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            installPreview(scenario);
            scenario.onActivity(a -> { mock(a).failNext = true; send(a, "Perth"); });
            await(scenario, a -> value(a, R.id.inputFeedback).contains("Demo request failed"));
            scenario.onActivity(a -> { assertEquals("Perth", value(a, R.id.messageInput)); click(a, R.id.inputFeedback); });
            await(scenario, a -> findText(a.findViewById(R.id.stepContent), "Perth") != null && value(a, R.id.messageInput).isEmpty());
            scenario.onActivity(a -> assertEquals("START", value(a, R.id.stepText)));
        }
    }
    @Test public void cachedTextFinalAnswerCanGenerateAgain() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences library = context.getSharedPreferences(TripStore.FILE, Context.MODE_PRIVATE);
        java.util.Map<String, ?> previous = library.getAll();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            installPreview(scenario);
            choose(scenario, "Sydney", "1/7"); choose(scenario, "Tokyo", "2/7"); chooseDates(scenario);
            scenario.onActivity(a -> { clickText(a, "Solo"); clickText(a, "Continue"); }); awaitStep(scenario, "4/7");
            choose(scenario, "Comfort", "5/7");
            scenario.onActivity(a -> { clickText(a, "No restrictions"); clickText(a, "Continue"); }); awaitStep(scenario, "6/7");
            choose(scenario, "Balanced", "7/7");
            scenario.onActivity(a -> send(a, "Food tour")); awaitStep(scenario, "AI PREVIEW"); approveGeneration(scenario);
            scenario.onActivity(a -> { click(a, R.id.backButton); assertEquals("7/7", value(a, R.id.stepText)); click(a, R.id.nextButton); });
            awaitStep(scenario, "AI PREVIEW"); approveGeneration(scenario);
        } finally {
            SharedPreferences.Editor restore = library.edit().clear();
            for (java.util.Map.Entry<String, ?> entry : previous.entrySet()) restore.putString(entry.getKey(), (String) entry.getValue());
            assertTrue(restore.commit());
        }
    }
    @Test public void cityCardsStayUntilExplicitConfirmation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            installPreview(scenario);
            scenario.onActivity(a -> {
                clickText(a, "Sydney");
                assertEquals("START", value(a, R.id.stepText));
                assertEquals(View.GONE, a.findViewById(R.id.tripBoard).getVisibility());
                clickText(a, "Confirm departure");
            });
            awaitStep(scenario, "1/7");
            scenario.onActivity(a -> {
                clickText(a, "Tokyo"); assertEquals("1/7", value(a, R.id.stepText));
                assertEquals("Depart from Sydney", value(a, R.id.tripBoardDestination));
                clickText(a, "Confirm destination");
            });
            awaitStep(scenario, "2/7");
        }
    }
    @Test public void dateEditCancelsOlderPendingConfirmation() {
        Calendar month = Calendar.getInstance(); month.set(Calendar.DAY_OF_MONTH, 1); month.add(Calendar.MONTH, 1);
        int base = TripDates.key(month) - 1;
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            installPreview(scenario);
            choose(scenario, "Sydney", "1/7"); choose(scenario, "Tokyo", "2/7");
            scenario.onActivity(a -> {
                findDescription(a.findViewById(R.id.main), "Next month").performClick();
                clickDate(a, base + 10); clickDate(a, base + 20);
                mock(a).delayMillis = 1500;
                clickText(a, "Confirm dates");
                clickDate(a, base + 25);
            });
            SystemClock.sleep(1800);
            scenario.onActivity(a -> {
                assertEquals("2/7", value(a, R.id.stepText));
                assertFalse(a.findViewById(R.id.nextButton).isEnabled());
                assertTrue(a.findViewById(R.id.stepContent).findViewWithTag("date-" + (base + 25)).isSelected());
                assertTrue(a.findViewById(R.id.sendButton).isEnabled());
            });
        }
    }
    @Test public void cachedEarlierConfirmationDoesNotGenerate() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences library = context.getSharedPreferences(TripStore.FILE, Context.MODE_PRIVATE);
        java.util.Map<String, ?> previous = library.getAll();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            installPreview(scenario);
            choose(scenario, "Sydney", "1/7"); choose(scenario, "Tokyo", "2/7"); chooseDates(scenario);
            scenario.onActivity(a -> { clickText(a, "Solo"); clickText(a, "Continue"); }); awaitStep(scenario, "4/7");
            choose(scenario, "Comfort", "5/7");
            scenario.onActivity(a -> { clickText(a, "No restrictions"); clickText(a, "Continue"); }); awaitStep(scenario, "6/7");
            choose(scenario, "Balanced", "7/7"); choose(scenario, "Local discoveries", "AI PREVIEW");
            scenario.onActivity(a -> { for (int i = 0; i < 4; i++) click(a, R.id.backButton); });
            awaitStep(scenario, "4/7"); choose(scenario, "Comfort", "5/7");
            scenario.onActivity(a -> assertFalse(value(a, R.id.stepText).equals("AI PREVIEW")));
        } finally {
            SharedPreferences.Editor restore = library.edit().clear();
            for (java.util.Map.Entry<String, ?> entry : previous.entrySet()) restore.putString(entry.getKey(), (String) entry.getValue());
            assertTrue(restore.commit());
        }
    }
    @Test public void backDuringResponseCannotHijackViewAndEditingRequiresReview() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            installPreview(scenario);
            choose(scenario, "Sydney", "1/7"); choose(scenario, "Tokyo", "2/7"); chooseDates(scenario);
            scenario.onActivity(a -> { send(a, "Three colleagues"); click(a, R.id.backButton); });
            SystemClock.sleep(500);
            scenario.onActivity(a -> assertEquals("2/7", value(a, R.id.stepText)));
            scenario.onActivity(a -> { click(a, R.id.backButton); click(a, R.id.backButton); send(a, "Perth"); });
            await(scenario, a -> findText(a.findViewById(R.id.stepContent), "Perth") != null);
            choose(scenario, "Perth", "1/7");
            scenario.onActivity(a -> {
                assertNotNull(findText(a.findViewById(R.id.stepContent), "Needs review · Previous answer: Tokyo"));
                assertFalse(a.findViewById(R.id.nextButton).isEnabled());
            });
        }
    }
    @Test public void calendarEarlierSecondTapRestartsAndDraftSurvivesRecreation() {
        Calendar month = Calendar.getInstance(); month.set(Calendar.DAY_OF_MONTH, 1); month.add(Calendar.MONTH, 1);
        int base = TripDates.key(month) - 1;
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            installPreview(scenario);
            choose(scenario, "Sydney", "1/7"); choose(scenario, "Tokyo", "2/7");
            scenario.onActivity(a -> {
                findDescription(a.findViewById(R.id.main), "Next month").performClick();
                clickDate(a, base + 20); clickDate(a, base + 10);
                assertFalse(a.findViewById(R.id.nextButton).isEnabled());
                assertEquals("Departure changed. Now choose your return date.", value(a, R.id.inputFeedback));
                clickDate(a, base + 25);
                ((TextView) a.findViewById(R.id.messageInput)).setText("Prefer autumn");
            });
            scenario.recreate();
            scenario.onActivity(a -> {
                assertEquals("2/7", value(a, R.id.stepText)); assertEquals("Prefer autumn", value(a, R.id.messageInput));
                assertTrue(a.findViewById(R.id.stepContent).findViewWithTag("date-" + (base + 10)).isSelected());
            });
        }
    }
    @Test public void restoredCalendarMonthDoesNotShiftInWesternTimezone() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            installPreview(scenario);
            scenario.onActivity(a -> {
                TimeZone original = TimeZone.getDefault();
                try {
                    TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
                    assertNotNull(findText(new TripCalendarView(a, new TripPlanState(), TripDates.calendar(20270101), date -> {}, () -> {}), "January 2027"));
                } finally { TimeZone.setDefault(original); }
            });
        }
    }
    static void chooseDates(ActivityScenario<MainActivity> scenario) {
        scenario.onActivity(a -> { a.date(20261210); a.date(20261214); a.confirm(); });
        awaitStep(scenario, "3/7");
    }
    private static void installPreview(ActivityScenario<MainActivity> scenario) {
        scenario.onActivity(a -> new androidx.lifecycle.ViewModelProvider(a).get(TripGenerationModel.class).clientFactory = () -> new TripPreviewClient() {
            @Override String preview(org.json.JSONObject request) throws Exception {
                TripPlanState plan = new TripPlanState();
                plan.selectDeparture(request.getString("departure")); plan.selectDestination(request.getString("destination"));
                plan.selectDate(Integer.parseInt(request.getString("startDate").replace("-", "")));
                plan.selectDate(Integer.parseInt(request.getString("endDate").replace("-", "")));
                plan.setCompanion(request.getString("companion")); plan.setBudget(request.getString("budget"));
                org.json.JSONArray constraints = request.getJSONArray("constraints");
                for (int i = 0; i < constraints.length(); i++) plan.toggleConstraint(constraints.getString(i));
                org.json.JSONObject notes = request.getJSONObject("notes");
                for (int i = 1; i <= 5; i++) plan.setNote(i, notes.optString(String.valueOf(i), ""));
                return MockItinerary.fromPlan(a.getApplicationContext(), plan, java.util.UUID.randomUUID().toString()).toString();
            }
        });
    }
    private static void approveGeneration(ActivityScenario<MainActivity> scenario) {
        scenario.onActivity(a -> {
            // The dialog is a separate window from stepContent.
            androidx.appcompat.app.AlertDialog dialog;
            try {
                java.lang.reflect.Field field = MainActivity.class.getDeclaredField("generationDialog"); field.setAccessible(true);
                dialog = (androidx.appcompat.app.AlertDialog) field.get(a);
            } catch (Exception e) { throw new AssertionError(e); }
            assertNotNull(dialog);
            View fields = dialog.getWindow().getDecorView();
            // Find text fields recursively because AppCompat may wrap the custom view.
            java.util.List<android.widget.EditText> inputs = new java.util.ArrayList<>(); collectInputs(fields, inputs);
            inputs.get(0).setText("2"); inputs.get(1).setText("AUD");
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).performClick();
        });
    }
    private static void collectInputs(View view, java.util.List<android.widget.EditText> result) {
        if (view instanceof android.widget.EditText) result.add((android.widget.EditText) view);
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) collectInputs(((ViewGroup) view).getChildAt(i), result);
    }
    static void choose(ActivityScenario<MainActivity> scenario, String title, String step) {
        scenario.onActivity(a -> {
            String before = value(a, R.id.stepText);
            clickText(a, title);
            if (before.equals("START")) clickText(a, "Confirm departure");
            else if (before.equals("1/7")) clickText(a, "Confirm destination");
        });
        awaitStep(scenario, step);
        if (step.equals("AI PREVIEW")) approveGeneration(scenario);
    }
    private static MockPlanningService mock(MainActivity activity) {
        try {
            java.lang.reflect.Field field = MainActivity.class.getDeclaredField("service"); field.setAccessible(true);
            return (MockPlanningService) field.get(activity);
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
    static void awaitStep(ActivityScenario<MainActivity> scenario, String step) { await(scenario, a -> value(a, R.id.stepText).equals(step)); }
    interface Check { boolean test(MainActivity activity); }
    static void await(ActivityScenario<MainActivity> scenario, Check check) {
        long end = SystemClock.uptimeMillis() + 10000; AtomicBoolean passed = new AtomicBoolean();
        do { scenario.onActivity(a -> passed.set(check.test(a))); if (passed.get()) return; SystemClock.sleep(50); } while (SystemClock.uptimeMillis() < end);
        fail("Planner did not reach the expected state within 10 seconds");
    }
    static void send(MainActivity a, String answer) { ((TextView) a.findViewById(R.id.messageInput)).setText(answer); click(a, R.id.sendButton); }
    static void click(MainActivity a, int id) { assertTrue(a.findViewById(id).performClick()); }
    static String value(MainActivity a, int id) { return ((TextView) a.findViewById(id)).getText().toString(); }
    static void clickDate(MainActivity a, int key) { View date = a.findViewById(R.id.stepContent).findViewWithTag("date-" + key); assertNotNull(date); date.performClick(); }
    static void clickText(MainActivity a, String text) {
        View found = findText(a.findViewById(R.id.stepContent), text); assertNotNull("Missing option: " + text, found);
        while (!found.isClickable() && found.getParent() instanceof View) found = (View) found.getParent();
        assertTrue(found.performClick());
    }
    static View findText(View view, String text) {
        if (view instanceof TextView && ((TextView) view).getText().toString().equals(text)) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = findText(((ViewGroup) view).getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
    static View findDescription(View view, String text) {
        if (text.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View found = findDescription(((ViewGroup) view).getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
}
