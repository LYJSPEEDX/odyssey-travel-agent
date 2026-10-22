package com.example.odyssey;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.graphics.ColorUtils;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.textfield.TextInputLayout;
import org.json.JSONObject;
import org.junit.Test;

/** 视觉检查发现的边界：安全区、聚焦文字对比度，以及连续重建时的待恢复草稿。 */
public class ProfileUiQaTest {
    @org.junit.Rule public final ProfileTestFixture profile = new ProfileTestFixture();

    @Test public void localProfileIsExcludedFromSystemBackupAndTransfer() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        for (int resource : new int[]{R.xml.backup_rules, R.xml.data_extraction_rules}) {
            int exclusions = 0;
            try (android.content.res.XmlResourceParser xml = context.getResources().getXml(resource)) {
                int event;
                while ((event = xml.next()) != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (event != org.xmlpull.v1.XmlPullParser.START_TAG || !"exclude".equals(xml.getName())) continue;
                    String path = xml.getAttributeValue(null, "path");
                    if ("file".equals(xml.getAttributeValue(null, "domain"))
                            && java.util.Arrays.asList("profile.json", "profile.json.bak", "profile.json.new").contains(path)) exclusions++;
                }
            }
            assertEquals("All atomic profile files must stay local", resource == R.xml.backup_rules ? 3 : 6, exclusions);
        }
    }

    @Test public void fieldsStayInsideSafeAreaAndFocusedLabelsAreReadable() {
        try (ActivityScenario<RegistrationActivity> scenario = openEditor()) {
            awaitReady(scenario);
            scenario.onActivity(activity -> {
                assertTrue(((ViewGroup) activity.findViewById(R.id.registrationRoot)).getClipToPadding());
                View view = activity.findViewById(R.id.registrationName);
                while (!(view instanceof TextInputLayout)) view = (View) view.getParent();
                TextInputLayout field = (TextInputLayout) view;
                assertNotNull("Focus label needs an explicit readable color", field.getHintTextColor());
                assertTrue(ColorUtils.calculateContrast(field.getHintTextColor().getDefaultColor(),
                        activity.getColor(R.color.background)) >= 4.5);
            });
        }
    }

    @Test public void pendingRestoredDraftSurvivesAnotherRecreation() {
        try (ActivityScenario<RegistrationActivity> scenario = openEditor()) {
            awaitReady(scenario);
            scenario.onActivity(activity -> {
                try {
                    // 模拟第一次旋转已取得草稿、磁盘读取尚未返回的确定性生命周期窗口。
                    java.lang.reflect.Field field = RegistrationActivity.class.getDeclaredField("restoredDraft");
                    field.setAccessible(true);
                    field.set(activity, new JSONObject().put("name", "Pending draft"));
                    activity.findViewById(R.id.registrationName).setEnabled(false);
                    Bundle saved = new Bundle();
                    activity.onSaveInstanceState(saved);
                    assertNotNull("Loading must not discard a restored draft", saved.getString("profile_draft"));
                    assertEquals("Pending draft", new JSONObject(saved.getString("profile_draft")).getString("name"));
                } catch (ReflectiveOperationException | org.json.JSONException error) {
                    throw new AssertionError(error);
                }
            });
        }
    }

    private ActivityScenario<RegistrationActivity> openEditor() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return ActivityScenario.launch(new Intent(context, RegistrationActivity.class)
                .putExtra(RegistrationActivity.EXTRA_EDIT_PROFILE, true));
    }

    private void awaitReady(ActivityScenario<RegistrationActivity> scenario) {
        java.util.concurrent.atomic.AtomicBoolean ready = new java.util.concurrent.atomic.AtomicBoolean();
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            scenario.onActivity(activity -> ready.set(activity.findViewById(R.id.registrationName).isEnabled()));
            if (ready.get()) return;
            SystemClock.sleep(25);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Profile did not finish loading");
    }
}
