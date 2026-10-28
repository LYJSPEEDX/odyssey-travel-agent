package com.example.odyssey;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.is;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;

/** 可跳过的资料必须真正保存为未提供，而不是只在界面上取消必填星号。 */
public class OptionalProfileFlowTest {
    @org.junit.Rule public final ProfileTestFixture fixture = new ProfileTestFixture();
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private void emptyProfile() throws Exception {
        java.nio.file.Files.delete(new java.io.File(context().getFilesDir(), "profile.json").toPath());
    }
    private void ready(ActivityScenario<RegistrationActivity> scenario) {
        long until = SystemClock.uptimeMillis() + 5000;
        java.util.concurrent.atomic.AtomicBoolean loaded = new java.util.concurrent.atomic.AtomicBoolean();
        while (!loaded.get() && SystemClock.uptimeMillis() < until) {
            scenario.onActivity(a -> loaded.set(a.findViewById(R.id.registrationNext).isEnabled()));
            if (!loaded.get()) SystemClock.sleep(20);
        }
        assertTrue(loaded.get());
    }
    @Test public void emptyDetailsCanFinishAndDoNotTriggerOnboardingAgain() throws Exception {
        emptyProfile();
        try (ActivityScenario<RegistrationActivity> scenario = ActivityScenario.launch(RegistrationActivity.class)) {
            ready(scenario);
            scenario.onActivity(a -> {
                assertEquals("", ((EditText) a.findViewById(R.id.registrationGender)).getText().toString());
                assertNull(a.findViewById(R.id.registrationAge));
                assertFalse(a.findViewById(R.id.registrationLocation).isShown());
                a.findViewById(R.id.registrationNext).performClick();
                assertFalse(a.findViewById(R.id.registrationName).isShown());
                assertTrue(a.findViewById(R.id.registrationLocation).isShown());
                a.findViewById(R.id.registrationNext).performClick();
            });
            ProfileStore store = new ProfileStore(context());
            long until = SystemClock.uptimeMillis() + 5000;
            while (!ProfileStore.isComplete(store.load()) && SystemClock.uptimeMillis() < until) SystemClock.sleep(20);
            JSONObject saved = store.load();
            assertTrue(ProfileStore.isComplete(saved));
            assertEquals(3, saved.getInt("schema_version"));
            for (String field : new String[]{"name", "gender", "birth_date", "nationality", "residential_location"})
                assertEquals(field, "", saved.getString(field));
            assertFalse(saved.has("age"));
            assertFalse(saved.has("legacy_age"));
        }
    }
    @Test public void nextBackAndRotationKeepDraftWithoutSaving() throws Exception {
        emptyProfile();
        try (ActivityScenario<RegistrationActivity> scenario = ActivityScenario.launch(RegistrationActivity.class)) {
            ready(scenario);
            scenario.onActivity(a -> {
                ((EditText) a.findViewById(R.id.registrationName)).setText("Alex");
                a.findViewById(R.id.registrationNext).performClick();
            });
            scenario.recreate(); ready(scenario);
            scenario.onActivity(a -> {
                assertTrue(a.findViewById(R.id.registrationLocation).isShown());
                a.findViewById(R.id.registrationPrevious).performClick();
                assertEquals("Alex", ((EditText) a.findViewById(R.id.registrationName)).getText().toString());
                assertTrue(a.findViewById(R.id.registrationGender).isShown());
            });
            assertNull(new ProfileStore(context()).load());
            EditorialLayoutTest.saveScreen(context(), "profile-step-one.png");
        }
    }
    @Test public void birthDateUsesDropdownsAndSupportsDeclining() throws Exception {
        emptyProfile();
        try (ActivityScenario<RegistrationActivity> scenario = ActivityScenario.launch(
                new Intent(context(), RegistrationActivity.class).putExtra(RegistrationActivity.EXTRA_EDIT_PROFILE, true))) {
            ready(scenario);
            scenario.onActivity(a -> a.findViewById(R.id.registrationBirthDate).performClick());
            SystemClock.sleep(350);
            onView(withContentDescription("Year")).perform(click());
            onData(is("2000")).inRoot(androidx.test.espresso.matcher.RootMatchers.isPlatformPopup()).perform(click());
            onView(withContentDescription("Month")).perform(click());
            onData(is("February")).inRoot(androidx.test.espresso.matcher.RootMatchers.isPlatformPopup()).perform(click());
            onView(withContentDescription("Day")).perform(click());
            onData(is("29")).inRoot(androidx.test.espresso.matcher.RootMatchers.isPlatformPopup()).perform(click());
            EditorialLayoutTest.saveScreen(context(), "profile-birthday-dropdowns.png");
            onView(withText("Done")).perform(click());
            scenario.onActivity(a -> assertEquals("2000-02-29", ((EditText) a.findViewById(R.id.registrationBirthDate)).getText().toString()));
            scenario.recreate(); ready(scenario);
            scenario.onActivity(a -> {
                assertEquals("2000-02-29", ((EditText) a.findViewById(R.id.registrationBirthDate)).getText().toString());
                a.findViewById(R.id.registrationBirthDate).performClick();
            });
            SystemClock.sleep(350);
            onView(withText("Prefer not to tell")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
            scenario.onActivity(a -> {
                assertEquals("Prefer not to tell", ((EditText) a.findViewById(R.id.registrationBirthDate)).getText().toString());
                a.findViewById(R.id.registrationNext).performClick();
                a.findViewById(R.id.registrationNext).performClick();
            });
            ProfileStore store = new ProfileStore(context());
            long until = SystemClock.uptimeMillis() + 5000;
            while (store.load() == null && SystemClock.uptimeMillis() < until) SystemClock.sleep(20);
            assertEquals("", store.load().getString("birth_date"));
        }
    }
}
