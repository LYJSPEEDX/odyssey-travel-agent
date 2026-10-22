package com.example.odyssey;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.Intent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;

public class RegistrationFlowTest {
    @org.junit.Rule public final ProfileTestFixture fixture = new ProfileTestFixture();
    @Test public void editPrefillsAndCancelDoesNotSave() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        java.io.File file = new java.io.File(context.getFilesDir(), "profile.json");
        ProfileStore store = new ProfileStore(context);
        try {
            org.json.JSONObject profile = new org.json.JSONObject().put("schema_version", 2).put("name", "Alex")
                    .put("gender", "Prefer not to say").put("age", 30).put("nationality", "Australian")
                    .put("residential_location", "Sydney").put("travel_preferences", "Art")
                    .put("preference_tags", new org.json.JSONArray().put("DIVING"));
            store.save(profile);
            Intent intent = new Intent(context, RegistrationActivity.class).putExtra(RegistrationActivity.EXTRA_EDIT_PROFILE, true);
            try (ActivityScenario<RegistrationActivity> scenario = ActivityScenario.launch(intent)) {
                awaitReady(scenario);
                scenario.onActivity(activity -> {
                    android.widget.EditText name = activity.findViewById(R.id.registrationName);
                    assertEquals("Alex", name.getText().toString());
                    name.setText("Unsaved");
                    activity.findViewById(R.id.registrationCancel).performClick();
                });
            }
            assertEquals("Alex", store.load().getString("name"));
        } finally { /* 由 fixture 恢复全部 AtomicFile 文件。 */ }
    }
    private static void awaitReady(ActivityScenario<RegistrationActivity> scenario) {
        java.util.concurrent.atomic.AtomicBoolean ready = new java.util.concurrent.atomic.AtomicBoolean();
        long deadline = android.os.SystemClock.uptimeMillis() + 5000;
        do {
            scenario.onActivity(activity -> ready.set(activity.findViewById(R.id.registrationName).isEnabled()));
            if (ready.get()) return;
            android.os.SystemClock.sleep(25);
        } while (android.os.SystemClock.uptimeMillis() < deadline);
        fail("Profile form did not finish loading");
    }
    @Test public void unsavedDraftSurvivesRotationAndSavePersists() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(context, RegistrationActivity.class).putExtra(RegistrationActivity.EXTRA_EDIT_PROFILE, true);
        try (ActivityScenario<RegistrationActivity> scenario = ActivityScenario.launch(intent)) {
            awaitReady(scenario);
            scenario.onActivity(activity -> ((android.widget.EditText) activity.findViewById(R.id.registrationName)).setText("Draft name"));
            scenario.recreate();
            awaitReady(scenario);
            scenario.onActivity(activity -> {
                assertEquals("Draft name", ((android.widget.EditText) activity.findViewById(R.id.registrationName)).getText().toString());
                findButton(activity.findViewById(R.id.registrationContent), "Save profile").performClick();
            });
            long deadline = android.os.SystemClock.uptimeMillis() + 5000;
            while (android.os.SystemClock.uptimeMillis() < deadline && !"Draft name".equals(new ProfileStore(context).load().getString("name")))
                android.os.SystemClock.sleep(25);
            assertEquals("Draft name", new ProfileStore(context).load().getString("name"));
        }
    }
    @Test public void missingAndLegacyProfilesRemainOnCompletionForm() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        java.nio.file.Files.delete(new java.io.File(context.getFilesDir(), "profile.json").toPath());
        try (ActivityScenario<RegistrationActivity> scenario = ActivityScenario.launch(RegistrationActivity.class)) {
            awaitReady(scenario);
            scenario.onActivity(activity -> assertEquals("", ((android.widget.EditText) activity.findViewById(R.id.registrationName)).getText().toString()));
        }
        new ProfileStore(context).save(29, "Melbourne", "Food");
        try (ActivityScenario<RegistrationActivity> scenario = ActivityScenario.launch(RegistrationActivity.class)) {
            awaitReady(scenario);
            scenario.onActivity(activity -> assertEquals("Melbourne", ((android.widget.EditText) activity.findViewById(R.id.registrationLocation)).getText().toString()));
        }
    }
    @Test public void homeGatesMissingProfileAndCompleteProfileBypassesForm() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        java.io.File file = new java.io.File(context.getFilesDir(), "profile.json");
        byte[] complete = java.nio.file.Files.readAllBytes(file.toPath());
        java.nio.file.Files.delete(file.toPath());
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(HomeActivity.class)) {
            awaitResumed(RegistrationActivity.class);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (android.app.Activity activity : androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED))
                    if (activity instanceof RegistrationActivity) activity.finish();
            });
        }
        java.nio.file.Files.write(file.toPath(), complete);
        try (ActivityScenario<RegistrationActivity> scenario = ActivityScenario.launch(RegistrationActivity.class)) {
            awaitResumed(HomeActivity.class);
        }
    }
    private static void awaitResumed(Class<?> type) {
        java.util.concurrent.atomic.AtomicBoolean found = new java.util.concurrent.atomic.AtomicBoolean();
        long deadline = android.os.SystemClock.uptimeMillis() + 5000;
        do {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (android.app.Activity activity : androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED))
                    if (type.isInstance(activity)) found.set(true);
            });
            if (found.get()) return;
            android.os.SystemClock.sleep(25);
        } while (android.os.SystemClock.uptimeMillis() < deadline);
        fail("Expected resumed " + type.getSimpleName());
    }
    private static android.view.View findButton(android.view.View view, String text) {
        if (view instanceof android.widget.Button && text.contentEquals(((android.widget.Button) view).getText())) return view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                android.view.View found = findButton(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
