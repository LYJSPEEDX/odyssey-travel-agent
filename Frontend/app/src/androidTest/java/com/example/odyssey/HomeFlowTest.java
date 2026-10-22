package com.example.odyssey;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import android.os.SystemClock;

@RunWith(AndroidJUnit4.class)
public class HomeFlowTest {
    @org.junit.Rule public final ProfileTestFixture profile = new ProfileTestFixture();
    @Test public void homeShowsCreateTripBanner() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent launcher = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        assertNotNull(launcher);
        assertEquals(HomeActivity.class.getName(), launcher.getComponent().getClassName());
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            scenario.onActivity(activity -> assertTrue(containsText(
                    activity.findViewById(android.R.id.content), context.getString(R.string.editorial_create))));
        }
    }

    @Test public void bannerOpensPlannerAndFirstBackReturnsHome() {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            awaitHome(scenario);
            onView(withId(R.id.createTripBanner)).perform(click());
            onView(withId(R.id.stepText)).check(matches(withText("01 / 05")));
            onView(withId(R.id.backButton)).perform(click());
            onView(withId(R.id.createTripBanner)).check(matches(isDisplayed()));
        }
    }

    @Test public void myTabShowsEmptyOrStoredStateAndReopensSnapshot() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        TripStore store = new TripStore(context);
        boolean initiallyEmpty = store.list().isEmpty();
        String id = UUID.randomUUID().toString();
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            awaitHome(scenario);
            scenario.onActivity(activity -> {
                ((BottomNavigationView) activity.findViewById(R.id.bottomNavigation)).setSelectedItemId(R.id.nav_my);
                assertEquals(initiallyEmpty, activity.findViewById(R.id.myEmptyState) != null);
            });
            assertTrue(store.save(new SavedTrip(id, System.currentTimeMillis(), "Test city",
                    "Test route " + id, "Flexible", 3, "Saved preference", "Saved day one")));
            scenario.onActivity(activity -> {
                BottomNavigationView nav = activity.findViewById(R.id.bottomNavigation);
                nav.setSelectedItemId(R.id.nav_home);
                nav.setSelectedItemId(R.id.nav_my);
                assertNull(activity.findViewById(R.id.myEmptyState));
                View trip = activity.findViewById(R.id.homeContent).findViewWithTag("saved-" + id);
                assertNotNull(trip);
                trip.performClick();
            });
            onView(withId(R.id.tripDetailContent)).check(matches(hasDescendant(withText("Saved day one"))));
            pressBack();
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals(R.id.nav_my,
                        ((BottomNavigationView) activity.findViewById(R.id.bottomNavigation)).getSelectedItemId());
                assertNotNull(activity.findViewById(R.id.homeContent).findViewWithTag("saved-" + id));
            });
        } finally {
            context.getSharedPreferences(TripStore.FILE, Context.MODE_PRIVATE)
                    .edit().remove("trip_" + id).commit(); // Remove only this test's own fixture.
        }
    }

    @Test public void settingsSaveNicknameThemeAndShowHonestLogin() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AppPreferences preferences = new AppPreferences(context);
        String previousName = preferences.nickname(context);
        int previousTheme = preferences.themeIndex();
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            awaitHome(scenario);
            scenario.onActivity(activity -> ((BottomNavigationView) activity.findViewById(R.id.bottomNavigation))
                    .setSelectedItemId(R.id.nav_settings));
            onView(withId(R.id.settingsLogin)).perform(click());
            onView(withText(R.string.settings_login_message)).check(matches(isDisplayed()));
            onView(withText(R.string.dialog_ok)).perform(click());
            onView(withId(R.id.settingsProfile)).perform(click());
            onView(withId(R.id.profileNickname)).perform(replaceText("Test traveller"));
            onView(withText(R.string.settings_save)).perform(click());
            assertEquals("Test traveller", new AppPreferences(context).nickname(context));
            onView(withId(R.id.settingsTheme)).perform(click());
            onView(withText("Dark")).perform(click());
            assertEquals(2, new AppPreferences(context).themeIndex());
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals(R.id.nav_settings,
                        ((BottomNavigationView) activity.findViewById(R.id.bottomNavigation)).getSelectedItemId());
                assertTrue(containsText(activity.findViewById(R.id.settingsVersion), "1.0 (1)"));
                assertTrue(containsText(activity.findViewById(R.id.settingsProfile), "Test traveller"));
            });
        } finally {
            preferences.saveNickname(previousName);
            preferences.saveTheme(previousTheme);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(preferences::applyTheme);
        }
    }

    /** 类入口启动等同桌面启动；等欢迎页正常结束再点击，不能点击被遮住的首页。 */
    private static void awaitHome(ActivityScenario<HomeActivity> scenario) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        AtomicBoolean ready = new AtomicBoolean(false);
        do {
            scenario.onActivity(activity -> ready.set(activity.findViewById(R.id.launchWelcome) == null));
            if (ready.get()) return;
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Welcome did not reveal home within five seconds");
    }

    private static boolean containsText(View view, String expected) {
        if (view instanceof TextView && expected.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                if (containsText(group.getChildAt(i), expected)) return true;
        }
        return false;
    }
}
