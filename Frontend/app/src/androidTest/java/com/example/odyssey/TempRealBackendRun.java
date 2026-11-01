package com.example.odyssey;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.Application;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import org.json.JSONObject;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicBoolean;

/** 临时：真实后端，点预设选项走完，生成时回首页。验证后删除。 */
public class TempRealBackendRun {
    @org.junit.Rule public final ProfileTestFixture profile = new ProfileTestFixture();
    private LivePlannerModel model;
    private interface Check { boolean ok() throws Exception; }
    private void await(long ms, String what, Check check) {
        long end = SystemClock.elapsedRealtime() + ms; AtomicBoolean ok = new AtomicBoolean();
        while (SystemClock.elapsedRealtime() < end) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> { try { ok.set(check.ok()); } catch (Exception e) { throw new RuntimeException(e); } });
            if (ok.get()) return;
            SystemClock.sleep(200);
        }
        shot("timeout"); fail("Timed out: " + what + " error=" + model.error);
    }
    private void shot(String name) {
        SystemClock.sleep(800);
        EditorialLayoutTest.saveScreen(InstrumentationRegistry.getInstrumentation().getTargetContext(), "real-" + name + ".png");
    }
    private Activity current() {
        Activity[] found = new Activity[1];
        Runnable read = () -> { for (Activity a : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) found[0] = a; };
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) read.run();
        else InstrumentationRegistry.getInstrumentation().runOnMainSync(read);
        return found[0];
    }
    private View findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View hit = findText(((ViewGroup) view).getChildAt(i), text); if (hit != null) return hit;
        }
        return null;
    }
    private void tap(String text) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View v = findText(current().findViewById(R.id.liveContent), text);
            assertNotNull("Missing " + text, v);
            while (!v.isClickable()) v = (View) v.getParent();
            v.performClick();
        });
    }
    private void tag(String tag) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View v = current().findViewById(R.id.liveContent).findViewWithTag(tag);
            assertNotNull("Missing tag " + tag, v); v.performClick();
        });
    }
    /** 等到这一步确认；助手追问时用明确的话回答，出现多个提议时选第一个。 */
    private void finish(int step, String fallback) throws Exception {
        String id = LivePlanningProtocol.STEPS[step];
        for (int round = 0; round < 5; round++) {
            await(360_000, "reply " + step, () -> !model.busy);
            Log.i("REALRUN", "step " + id + " round " + round + " outcome=" + model.session.optString("outcome") + " error=" + model.error + " msg=" + model.session.optString("assistantMessage"));
            if ("CONFIRMED".equals(model.session.getJSONObject("steps").getJSONObject(id).getString("state"))) return;
            JSONObject proposal = model.session.optJSONObject("proposal");
            if (proposal != null) {
                shot(String.format("%02d-proposal", step + 1));
                String head = LivePlanningProtocol.headline(id, proposal.getJSONArray("options").getJSONObject(0).getJSONObject("answer"));
                tap(head); SystemClock.sleep(300); shot(String.format("%02d-selected", step + 1));
                tag("live-confirm");
            } else {
                shot(String.format("%02d-clarify", step + 1));
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                    ((android.widget.EditText) current().findViewById(R.id.liveInput)).setText(fallback);
                    current().findViewById(R.id.liveSend).performClick();
                });
            }
        }
        fail("Step not confirmed: " + id);
    }
    @Test public void run() throws Exception {
        Application app = (Application) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        model = LivePlannerModel.get(app);
        Log.i("REALRUN", "endpoint " + model.endpoint);
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            SystemClock.sleep(2500);
            scenario.onActivity(a -> a.findViewById(R.id.createTripBanner).performClick());
            await(60_000, "session", () -> current() instanceof LivePlannerActivity && !model.busy && model.session != null);
            shot("00-start");
            tap(LivePlanningProtocol.originSuggestions(model.creation).get(0));
            finish(0, "Departing from Sydney, Australia.");
            await(5000, "dest", () -> model.selected == 1); SystemClock.sleep(400); shot("01-destination");
            tap("Tokyo, Japan"); finish(1, "Destination Tokyo, Japan.");
            await(5000, "dates", () -> model.selected == 2); shot("02-dates");
            java.util.Calendar c = java.util.Calendar.getInstance(); c.add(java.util.Calendar.DAY_OF_MONTH, 2);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> { model.dates.select(TripDates.key(c)); c.add(java.util.Calendar.DAY_OF_MONTH, 3); model.dates.select(TripDates.key(c)); model.select(2); });
            SystemClock.sleep(600); tag("live-dates"); finish(2, "Those exact fixed dates.");
            await(5000, "who", () -> model.selected == 3); shot("03-companions");
            tap("Solo"); finish(3, "One adult, no children."); 
            await(5000, "budget", () -> model.selected == 4); shot("04-budget");
            tap("Comfort"); finish(4, "AUD 280 per person per day, flights not included.");
            await(5000, "constraints", () -> model.selected == 5); SystemClock.sleep(300); tap("Vegetarian"); tap("Less walking"); shot("05-constraints");
            tag("live-continue"); finish(5, "Vegetarian and less walking are preferred.");
            await(5000, "pace", () -> model.selected == 6); shot("06-pace");
            tag("live-skip"); finish(6, "A balanced pace.");
            await(5000, "exp", () -> model.selected == 7); shot("07-experience");
            tag("live-skip"); finish(7, "General sightseeing.");
            await(10_000, "ready", () -> model.session.getJSONObject("generationReadiness").getBoolean("ready"));
            shot("08-ready");
            tag("live-generate");
            await(30_000, "generating", () -> model.busy && model.generationUncertain);
            shot("09-generating");
            tag("live-home");
            await(10_000, "home", () -> current() instanceof HomeActivity);
            shot("10-home-while-generating");
            long began = SystemClock.elapsedRealtime();
            Log.i("REALRUN", "returned home, still busy=" + model.busy);
            await(1_300_000, "generation", () -> !model.busy);
            Log.i("REALRUN", "generation ms=" + (SystemClock.elapsedRealtime() - began) + " saved=" + model.savedTripId + " error=" + model.error);
            shot("11-home-after");
            assertFalse("Generation error: " + model.error, model.savedTripId.isEmpty());
            assertNotNull(new TripStore(app).get(model.savedTripId));
        }
    }
}
