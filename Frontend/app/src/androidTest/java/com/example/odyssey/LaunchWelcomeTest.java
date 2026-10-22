package com.example.odyssey;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class LaunchWelcomeTest {
    @org.junit.Rule public final ProfileTestFixture profile = new ProfileTestFixture();
    private Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }
    private View welcome(Activity activity) {
        int id = context().getResources().getIdentifier("launchWelcome", "id", context().getPackageName());
        return id == 0 ? null : activity.findViewById(id);
    }
    @Test public void launcherShowsTicketThenRevealsExistingHome() {
        Intent intent = context().getPackageManager().getLaunchIntentForPackage(context().getPackageName());
        assertNotNull(intent);
        try (ActivityScenario<Activity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> assertNotNull("Launcher must show native ticket", welcome(activity)));
            SystemClock.sleep(1800);
            scenario.onActivity(activity -> {
                assertNull(welcome(activity));
                assertTrue(activity.findViewById(R.id.createTripBanner).isShown());
            });
            scenario.recreate();
            scenario.onActivity(activity -> assertNull("Recreation must not replay welcome", welcome(activity)));
        }
    }
    @Test public void internalHomeNavigationSkipsWelcome() {
        Intent intent = new Intent(context(), HomeActivity.class).putExtra(HomeActivity.EXTRA_OPEN_MY, true);
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> assertNull(welcome(activity)));
        }
    }

    @Test public void backgroundPausesCountdownAndRecreationKeepsWelcome() {
        Intent intent = context().getPackageManager().getLaunchIntentForPackage(context().getPackageName());
        try (ActivityScenario<Activity> scenario = ActivityScenario.launch(intent)) {
            scenario.moveToState(Lifecycle.State.CREATED);
            SystemClock.sleep(1800);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> assertNotNull("Background must not consume timer", welcome(activity)));
            scenario.recreate();
            scenario.onActivity(activity -> assertNotNull("Pending welcome survives rotation", welcome(activity)));
            SystemClock.sleep(1800);
            scenario.onActivity(activity -> assertNull(welcome(activity)));
        }
    }

    @Test public void ticketFitsSmallPortraitLandscapeAndLargeText() {
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(
                new Intent(context(), HomeActivity.class).putExtra(HomeActivity.EXTRA_OPEN_MY, true))) {
            scenario.onActivity(activity -> {
                renderPreview(activity, 320, 640, 1f);
                renderPreview(activity, 400, 850, 1f);
                renderPreview(activity, 800, 400, 1f);
                renderPreview(activity, 400, 850, 2f);
            });
        }
    }

    @Test public void systemSplashUsesOdysseyInsteadOfTemplateAndroidIcon() throws Exception {
        if (Build.VERSION.SDK_INT < 31) return;
        int launchTheme = context().getPackageManager().getActivityInfo(
                new ComponentName(context(), HomeActivity.class), 0).getThemeResource();
        Context themed = new ContextThemeWrapper(context(), launchTheme);
        TypedArray attributes = themed.obtainStyledAttributes(new int[]{android.R.attr.windowSplashScreenAnimatedIcon});
        try {
            int expected = context().getResources().getIdentifier("ic_odyssey_splash", "drawable", context().getPackageName());
            assertTrue("A branded splash icon must exist", expected != 0);
            assertEquals(expected, attributes.getResourceId(0, 0));
        } finally {
            attributes.recycle();
        }
    }

    /** 直接渲染真实 XML 控件，同时生成检查用截图，不需要暂停或改变正式 1.5 秒时长。 */
    private void renderPreview(Activity activity, int widthDp, int heightDp, float fontScale) {
        Configuration config = new Configuration(activity.getResources().getConfiguration());
        config.fontScale = fontScale;
        Context themed = new ContextThemeWrapper(activity.createConfigurationContext(config), R.style.Theme_Odyssey);
        View root = LayoutInflater.from(themed).inflate(R.layout.activity_launch, null, false);
        float density = themed.getResources().getDisplayMetrics().density;
        int width = Math.round(widthDp * density), height = Math.round(heightDp * density);
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        View ticket = root.findViewById(R.id.launchTicket);
        // 整体缩放让文字、圆角、虚线、条码同步变成原先的一半，而不是只缩窄容器。
        assertEquals(0.5f, ticket.getScaleX(), 0.001f);
        assertEquals(0.5f, ticket.getScaleY(), 0.001f);
        assertTrue(ticket.getWidth() > 0 && ticket.getHeight() > 0);
        assertTrue(ticket.getLeft() >= 0 && ticket.getRight() <= width);
        assertTrue(ticket.getTop() >= 0 && ticket.getBottom() <= height);
        assertEquals(382f / 582f, (float) ticket.getWidth() / ticket.getHeight(), 0.01f);
        checkTextFits(ticket);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        File image = new File(activity.getExternalFilesDir("launch-previews"),
                "launch-" + widthDp + "x" + heightDp + "-font" + fontScale + ".png");
        try (FileOutputStream output = new FileOutputStream(image)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        } finally {
            bitmap.recycle();
        }
    }

    private void checkTextFits(View view) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            assertNotNull(text.getLayout());
            assertTrue(text.getText().toString(), text.getLayout().getHeight()
                    <= text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom());
            for (int line = 0; line < text.getLayout().getLineCount(); line++) {
                assertEquals(0, text.getLayout().getEllipsisCount(line));
                assertTrue(text.getText().toString(), text.getLayout().getLineWidth(line)
                        <= text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight() + 1);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) checkTextFits(group.getChildAt(i));
        }
    }
}
