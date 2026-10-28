package com.example.odyssey;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** 验证离线国家/地区目录、渐进兴趣标签和只读选择框。 */
public class ProfileSelectionTest {
    @org.junit.Rule public final ProfileTestFixture fixture = new ProfileTestFixture();

    @Test public void offlineCountriesAndSubdivisionsAreAvailable() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        RegionCatalog catalog = new RegionCatalog(context);
        assertTrue(catalog.countries().size() > 200);
        assertTrue(catalog.regions("AU").contains("New South Wales"));
        assertTrue(catalog.regions("CN").contains("Beijing"));
        assertTrue(catalog.regions("US").contains("New York"));
    }

    @Test public void interestsExpandAndSelectionsAndExistingLocationSurviveRotation() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        new ProfileStore(context).save(new JSONObject().put("schema_version", 2).put("name", "Alex")
                .put("gender", "Prefer not to say").put("age", 30).put("nationality", "Australian")
                .put("residential_location", "Sydney").put("travel_preferences", "")
                .put("preference_tags", new JSONArray()));
        try (ActivityScenario<RegistrationActivity> scenario = ActivityScenario.launch(
                new Intent(context, RegistrationActivity.class).putExtra(RegistrationActivity.EXTRA_EDIT_PROFILE, true))) {
            awaitReady(scenario);
            EditorialLayoutTest.saveScreen(context, "profile-fields-preview.png");
            scenario.onActivity(activity -> activity.findViewById(R.id.registrationNext).performClick());
            scenario.onActivity(activity -> {
                ChipGroup group = findTags(activity.findViewById(R.id.registrationContent));
                ((androidx.core.widget.NestedScrollView) activity.findViewById(R.id.registrationRoot))
                        .scrollTo(0, Math.max(0, group.getTop() - 100));
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            android.os.SystemClock.sleep(250); // 等待系统合成下一帧，再保存实际界面预览。
            EditorialLayoutTest.saveScreen(context, "profile-tags-preview.png");
            scenario.onActivity(activity -> {
                ChipGroup tags = findTags(activity.findViewById(R.id.registrationContent));
                assertNotNull(tags);
                assertNull(activity.findViewById(R.id.registrationPreferences));
                assertNull(findButton(activity.findViewById(R.id.registrationContent), "Choose country"));
                int visible = 0;
                for (int i = 0; i < tags.getChildCount(); i++) if (tags.getChildAt(i).getVisibility() == View.VISIBLE) visible++;
                assertEquals(12, visible);
                Chip water = findChip(tags, "WATER_ACTIVITIES"), diving = findChip(tags, "DIVING");
                assertEquals(View.GONE, diving.getVisibility());
                water.setChecked(true); assertEquals(View.VISIBLE, diving.getVisibility()); diving.setChecked(true);
                assertTrue(tags.indexOfChild(water) >= 0);
                assertNotSame(water.getParent(), diving.getParent());
                assertFalse(diving.isCheckedIconVisible());
                assertTrue(diving.getTypeface().isBold());
                assertFalse(activity.findViewById(R.id.registrationNationality).isFocusable());
                assertFalse(activity.findViewById(R.id.registrationLocation).isFocusable());
                assertEquals("Sydney", ((EditText) activity.findViewById(R.id.registrationLocation)).getText().toString());
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            android.os.SystemClock.sleep(700); // 最后一个错峰动画也应结束，截图覆盖真正展开后的布局。
            EditorialLayoutTest.saveScreen(context, "profile-tags-expanded.png");
            scenario.onActivity(activity -> {
                ChipGroup roots = findTags(activity.findViewById(R.id.registrationContent));
                Chip diving = findChip(roots, "DIVING");
                assertEquals(1f, diving.getAlpha(), 0.01f);
                assertTrue(((View) diving.getParent()).getTop() >= roots.getBottom());
            });
            scenario.recreate(); awaitReady(scenario);
            scenario.onActivity(activity -> {
                ChipGroup tags = findTags(activity.findViewById(R.id.registrationContent));
                assertTrue(findChip(tags, "DIVING").isChecked());
                assertEquals(View.VISIBLE, findChip(tags, "DIVING").getVisibility());
                assertEquals("Sydney", ((EditText) activity.findViewById(R.id.registrationLocation)).getText().toString());
            });
        }
    }
    @Test public void userCanSelectCountryAndRegionWithoutTyping() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (ActivityScenario<RegistrationActivity> scenario = ActivityScenario.launch(
                new Intent(context, RegistrationActivity.class).putExtra(RegistrationActivity.EXTRA_EDIT_PROFILE, true))) {
            awaitReady(scenario);
            scenario.onActivity(activity -> { activity.findViewById(R.id.registrationNext).performClick(); activity.findViewById(R.id.registrationLocation).performClick(); });
            androidx.test.espresso.Espresso.onData(org.hamcrest.Matchers.is("Australia"))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(androidx.test.espresso.action.ViewActions.click());
            androidx.test.espresso.Espresso.onData(org.hamcrest.Matchers.is("New South Wales"))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(androidx.test.espresso.action.ViewActions.click());
            scenario.onActivity(activity -> assertEquals("New South Wales, Australia",
                    ((EditText) activity.findViewById(R.id.registrationLocation)).getText().toString()));
        }
    }
    @Test public void genderIsAWhiteSelectionFieldAndSavesAcrossRotation() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (ActivityScenario<RegistrationActivity> scenario = ActivityScenario.launch(
                new Intent(context, RegistrationActivity.class).putExtra(RegistrationActivity.EXTRA_EDIT_PROFILE, true))) {
            awaitReady(scenario);
            scenario.onActivity(activity -> activity.findViewById(R.id.registrationGender).performClick());
            android.os.SystemClock.sleep(350); // 等待弹窗入场，截图用于验证实际点击的列表。
            EditorialLayoutTest.saveScreen(context, "gender-dialog.png");
            androidx.test.espresso.Espresso.onData(org.hamcrest.Matchers.is("Non-binary"))
                    .inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(androidx.test.espresso.action.ViewActions.click());
            // ListView 会延迟派发 item click；等待回调完成再验证，避免与按压动画竞争。
            long selectionDeadline = android.os.SystemClock.uptimeMillis() + 3000;
            java.util.concurrent.atomic.AtomicBoolean selected = new java.util.concurrent.atomic.AtomicBoolean();
            while (!selected.get() && android.os.SystemClock.uptimeMillis() < selectionDeadline) {
                scenario.onActivity(activity -> selected.set("Non-binary".contentEquals(
                        ((EditText) activity.findViewById(R.id.registrationGender)).getText())));
                if (!selected.get()) android.os.SystemClock.sleep(25);
            }
            EditorialLayoutTest.saveScreen(context, "gender-selected.png");
            scenario.onActivity(activity -> {
                assertEquals("Selected value before recreation", "Non-binary",
                        ((EditText) activity.findViewById(R.id.registrationGender)).getText().toString());
                android.os.Bundle draft = new android.os.Bundle(); activity.onSaveInstanceState(draft);
                try { assertEquals("Non-binary", new JSONObject(draft.getString("profile_draft")).getString("gender")); }
                catch (org.json.JSONException error) { throw new AssertionError(error); }
            });
            scenario.recreate(); awaitReady(scenario);
            scenario.onActivity(activity -> {
                EditText gender = activity.findViewById(R.id.registrationGender);
                assertEquals("Non-binary", gender.getText().toString());
                assertFalse(gender.isFocusable());
            });
        }
    }
    @Test public void whiteInputsRemainReadableInDarkTheme() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (ActivityScenario<RegistrationActivity> scenario = ActivityScenario.launch(
                new Intent(context, RegistrationActivity.class).putExtra(RegistrationActivity.EXTRA_EDIT_PROFILE, true))) {
            awaitReady(scenario);
            scenario.onActivity(activity -> activity.getDelegate().setLocalNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            awaitReady(scenario);
            scenario.onActivity(activity -> {
                EditText input = activity.findViewById(R.id.registrationGender);
                View parent = input;
                while (!(parent instanceof com.google.android.material.textfield.TextInputLayout)) parent = (View) parent.getParent();
                com.google.android.material.textfield.TextInputLayout box = (com.google.android.material.textfield.TextInputLayout) parent;
                assertEquals(android.graphics.Color.WHITE, box.getBoxBackgroundColor());
                View location = activity.findViewById(R.id.registrationLocation);
                while (!(location instanceof com.google.android.material.textfield.TextInputLayout)) location = (View) location.getParent();
                android.widget.ImageButton icon = location.findViewById(com.google.android.material.R.id.text_input_end_icon);
                // Material 把 tint 直接应用到 Drawable，ImageView 的 tint 属性可以是 null。
                android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(24, 24, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.drawable.Drawable drawable = icon.getDrawable();
                android.graphics.Rect bounds = new android.graphics.Rect(drawable.getBounds());
                drawable.setBounds(0, 0, 24, 24); drawable.draw(new android.graphics.Canvas(bitmap));
                drawable.setBounds(bounds);
                assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(bitmap.getPixel(12, 12), android.graphics.Color.WHITE) >= 4.5);
                bitmap.recycle();
                assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(input.getCurrentTextColor(), android.graphics.Color.WHITE) >= 4.5);
                assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(box.getDefaultHintTextColor().getDefaultColor(), android.graphics.Color.WHITE) >= 4.5);
            });
            EditorialLayoutTest.saveScreen(context, "profile-dark-preview.png");
        }
    }
    private static View findButton(View view, String label) {
        if (view instanceof android.widget.Button && label.contentEquals(((android.widget.Button) view).getText())) return view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findButton(group.getChildAt(i), label); if (found != null) return found;
            }
        }
        return null;
    }
    private static Chip findChip(ChipGroup group, String id) {
        // 根标签与衍生标签分成上下两组，仍以同一个稳定业务 ID 查找。
        android.view.ViewGroup content = (android.view.ViewGroup) group.getParent();
        for (int j = 0; j < content.getChildCount(); j++) {
            if (!(content.getChildAt(j) instanceof ChipGroup)) continue;
            ChipGroup candidate = (ChipGroup) content.getChildAt(j);
            for (int i = 0; i < candidate.getChildCount(); i++)
                if (id.equals(candidate.getChildAt(i).getTag())) return (Chip) candidate.getChildAt(i);
        }
        throw new AssertionError("Missing interest: " + id);
    }
    private static ChipGroup findTags(View view) {
        if (view instanceof ChipGroup) return (ChipGroup) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                ChipGroup found = findTags(group.getChildAt(i)); if (found != null) return found;
            }
        }
        return null;
    }
    private static void awaitReady(ActivityScenario<RegistrationActivity> scenario) {
        long deadline = android.os.SystemClock.uptimeMillis() + 5000;
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            java.util.concurrent.atomic.AtomicBoolean ready = new java.util.concurrent.atomic.AtomicBoolean();
            scenario.onActivity(activity -> ready.set(activity.findViewById(R.id.registrationName).isEnabled()));
            if (ready.get()) return;
            android.os.SystemClock.sleep(25);
        }
        fail("Profile did not finish loading");
    }
}
