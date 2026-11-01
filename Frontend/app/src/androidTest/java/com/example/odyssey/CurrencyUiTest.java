package com.example.odyssey;

import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class CurrencyUiTest {
    @org.junit.Rule public final ProfileTestFixture fixture = new ProfileTestFixture();
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private void australiaProfile() throws Exception {
        new ProfileStore(context()).save(new JSONObject().put("schema_version", 3).put("onboarding_complete", true)
                .put("name", "").put("gender", "").put("birth_date", "").put("nationality", "")
                .put("residential_location", "New South Wales, Australia").put("residence_country_code", "AU")
                .put("currency_override", "").put("travel_preferences", "").put("preference_tags", new JSONArray()));
    }
    @Test public void suggestedCurrencyAndManualChoiceSurviveRotationAndSave() throws Exception {
        australiaProfile();
        try (ActivityScenario<RegistrationActivity> scenario = ActivityScenario.launch(
                new Intent(context(), RegistrationActivity.class).putExtra(RegistrationActivity.EXTRA_EDIT_PROFILE, true))) {
            ready(scenario);
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.registrationNext).performClick();
                assertEquals("AUD · Suggested", ((EditText) activity.findViewById(R.id.profileCurrency)).getText().toString());
                activity.findViewById(R.id.profileCurrency).performClick();
            });
            onData(org.hamcrest.Matchers.is("JPY")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
            scenario.recreate(); ready(scenario);
            scenario.onActivity(activity -> {
                assertEquals("JPY · Selected", ((EditText) activity.findViewById(R.id.profileCurrency)).getText().toString());
                activity.findViewById(R.id.registrationNext).performClick();
            });
            ProfileStore store = new ProfileStore(context());
            long end = SystemClock.uptimeMillis() + 5000;
            while (!"JPY".equals(store.load().optString("currency_override")) && SystemClock.uptimeMillis() < end) SystemClock.sleep(25);
            assertEquals("JPY", store.load().getString("currency_override"));
            assertEquals("AU", store.load().getString("residence_country_code"));
        }
    }
    private void ready(ActivityScenario<RegistrationActivity> scenario) {
        long end = SystemClock.uptimeMillis() + 5000;
        java.util.concurrent.atomic.AtomicBoolean loaded = new java.util.concurrent.atomic.AtomicBoolean();
        while (!loaded.get() && SystemClock.uptimeMillis() < end) {
            scenario.onActivity(a -> loaded.set(a.findViewById(R.id.registrationNext).isEnabled()));
            if (!loaded.get()) SystemClock.sleep(20);
        }
        assertTrue(loaded.get());
    }
}
