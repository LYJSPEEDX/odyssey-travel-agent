package com.example.odyssey;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;

/** 使用真实窗口验证票券大小、向上移动，以及首次资料页和欢迎层的衔接。 */
public class WelcomeMotionTest {
    @org.junit.Rule public final ProfileTestFixture fixture = new ProfileTestFixture();
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }

    @Test public void ticketSlidesUpWithoutScalingAndRevealsProfile() {
        Intent intent = new Intent(context(), RegistrationActivity.class)
                .putExtra(RegistrationActivity.EXTRA_EDIT_PROFILE, true)
                .putExtra(RegistrationActivity.EXTRA_WELCOME_REMAINING, 1200L);
        try (ActivityScenario<RegistrationActivity> scenario = ActivityScenario.launch(intent)) {
            float[] size = new float[2];
            scenario.onActivity(activity -> {
                View ticket = activity.findViewById(R.id.launchTicket);
                assertNotNull(ticket);
                size[0] = ticket.getWidth() * ticket.getScaleX();
                size[1] = ticket.getHeight() * ticket.getScaleY();
                assertTrue(size[0] > 0);
                assertEquals(View.INVISIBLE, activity.findViewById(R.id.registrationRoot).getVisibility());
            });
            EditorialLayoutTest.saveScreen(context(), "welcome-motion-start.png");
            java.util.concurrent.atomic.AtomicBoolean moving = new java.util.concurrent.atomic.AtomicBoolean();
            long deadline = SystemClock.uptimeMillis() + 3000;
            while (!moving.get() && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity(activity -> {
                    View overlay = activity.findViewById(R.id.launchWelcome);
                    if (overlay == null || overlay.getTranslationY() >= -30) return;
                    View ticket = activity.findViewById(R.id.launchTicket);
                    assertEquals(size[0], ticket.getWidth() * ticket.getScaleX(), 1f);
                    assertEquals(size[1], ticket.getHeight() * ticket.getScaleY(), 1f);
                    assertEquals(View.VISIBLE, activity.findViewById(R.id.registrationRoot).getVisibility());
                    moving.set(true);
                });
                if (!moving.get()) SystemClock.sleep(20);
            }
            assertTrue("Ticket should move upwards", moving.get());
            SystemClock.sleep(250);
            EditorialLayoutTest.saveScreen(context(), "welcome-motion-middle.png");
            SystemClock.sleep(1000);
            scenario.onActivity(activity -> {
                assertNull(activity.findViewById(R.id.launchWelcome));
                assertEquals(1f, activity.findViewById(R.id.registrationRoot).getAlpha(), 0.001f);
                assertEquals(0f, activity.findViewById(R.id.registrationRoot).getTranslationY(), 0.001f);
                assertTrue(activity.findViewById(R.id.registrationName).isShown());
            });
            EditorialLayoutTest.saveScreen(context(), "welcome-motion-end.png");
            scenario.recreate();
            scenario.onActivity(activity -> assertNull("Recreation must not replay", activity.findViewById(R.id.launchWelcome)));
        }
    }

    @Test public void missingProfileReceivesTheWelcomeWithoutShowingHomeFirst() throws Exception {
        java.io.File profile = new java.io.File(context().getFilesDir(), "profile.json");
        java.nio.file.Files.delete(profile.toPath());
        Intent intent = context().getPackageManager().getLaunchIntentForPackage(context().getPackageName());
        try (ActivityScenario<android.app.Activity> scenario = ActivityScenario.launch(intent)) {
            java.util.concurrent.atomic.AtomicBoolean arrived = new java.util.concurrent.atomic.AtomicBoolean();
            long deadline = SystemClock.uptimeMillis() + 1200;
            while (!arrived.get() && SystemClock.uptimeMillis() < deadline) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                    for (android.app.Activity activity : androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)) {
                        if (!(activity instanceof RegistrationActivity)) continue;
                        assertTrue(activity.getIntent().getLongExtra(RegistrationActivity.EXTRA_WELCOME_REMAINING, 0) > 0);
                        assertNotNull(activity.findViewById(R.id.launchTicket));
                        arrived.set(true);
                    }
                });
                if (!arrived.get()) SystemClock.sleep(20);
            }
            assertTrue("First launch must transfer the same welcome to profile", arrived.get());
            // 结束测试创建的资料页，让 fixture 能安全恢复用户原存档。
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (android.app.Activity activity : androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED))
                    if (activity instanceof RegistrationActivity) activity.finish();
            });
        }
    }
}
