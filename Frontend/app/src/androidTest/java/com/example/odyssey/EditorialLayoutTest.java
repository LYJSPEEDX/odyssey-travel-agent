package com.example.odyssey;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;

/** 在不同宽度、字体和主题下实际测量/绘制原生 View，不修改用户设备全局设置。 */
@RunWith(AndroidJUnit4.class)
public class EditorialLayoutTest {
    @org.junit.Rule public final ProfileTestFixture profile = new ProfileTestFixture();
    @Test public void coversFitSmallScreensAndLargeTypeInBothThemes() {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int width : new int[]{320, 400}) for (float scale : new float[]{1, 2})
                for (boolean dark : new boolean[]{false, true}) {
                    Configuration config = new Configuration(base.getResources().getConfiguration());
                    config.screenWidthDp = width; config.fontScale = scale;
                    config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                            | (dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
                    Context themed = new ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_Odyssey);
                    LinearLayout root = new LinearLayout(themed); root.setOrientation(LinearLayout.VERTICAL);
                    HomeUi ui = new HomeUi(root); root.setBackgroundColor(ui.color(R.color.background));
                    root.setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16));
                    SavedTrip trip = ExampleTrips.all(themed).get(0);
                    View card = TripCardView.add(ui, trip, true, () -> {}, null);
                    TripPlanState state = new TripPlanState(); state.selectDeparture("Sydney"); state.selectDestination("Kyoto");
                    DestinationCards.add(root, state, city -> {});
                    root.measure(View.MeasureSpec.makeMeasureSpec(ui.dp(width), View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                    root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
                    View cover = card.findViewById(R.id.editorialCover);
                    TextView title = card.findViewById(R.id.editorialTitle);
                    TextView badge = card.findViewById(R.id.editorialBadge);
                    // 离屏 View 没有窗口，getLocationInWindow 会返回零；使用共同父节点坐标。
                    android.graphics.Rect titleBounds = new android.graphics.Rect(0, 0, title.getWidth(), title.getHeight());
                    android.graphics.Rect badgeBounds = new android.graphics.Rect(0, 0, badge.getWidth(), badge.getHeight());
                    root.offsetDescendantRectToMyCoords(title, titleBounds);
                    root.offsetDescendantRectToMyCoords(badge, badgeBounds);
                    assertTrue("Photo title cannot overlap label: " + width + "/" + scale,
                            titleBounds.top >= badgeBounds.bottom);
                    assertTrue("Photo title fits cover", title.getHeight() <= cover.getHeight());
                    assertTrue("Card stays inside content width", card.getWidth() <= root.getWidth() - root.getPaddingLeft() - root.getPaddingRight());
                    save(root, "cards-" + width + "-font" + scale + (dark ? "-dark" : "-light") + ".png");
                }
        });
    }
    @Test public void captureNativeHomeAndDestinationScreens() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (ActivityScenario<HomeActivity> scenario = ActivityScenario.launch(new Intent(context, HomeActivity.class)
                .putExtra(HomeActivity.EXTRA_OPEN_MY, true))) {
            scenario.onActivity(activity -> ((BottomNavigationView) activity.findViewById(R.id.bottomNavigation))
                    .setSelectedItemId(R.id.nav_home));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            saveScreen(context, "native-home.png");
        }
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            PlannerFlowTest.choose(scenario, "Sydney", "1/7");
            scenario.onActivity(activity -> {
                PlannerFlowTest.clickText(activity, "Kyoto");
                assertEquals("1/7", PlannerFlowTest.value(activity, R.id.stepText));
                assertNotNull(PlannerFlowTest.findText(activity.findViewById(R.id.stepContent), "Confirm destination"));
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            saveScreen(context, "native-planner.png");
        }
    }
    private static void save(View root, String filename) {
        Bitmap image = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(image));
        writeImage(root.getContext(), image, filename);
    }
    /** 实机截图保留硬件裁剪圆角；离屏 Canvas 只适合测量布局，不能验证 GPU outline。 */
    private static void saveScreen(Context context, String filename) {
        Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull("Device screenshot available", image);
        writeImage(context, image, filename);
    }
    private static void writeImage(Context context, Bitmap image, String filename) {
        File folder = new File(context.getExternalFilesDir(null), "editorial-previews");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        try (FileOutputStream file = new FileOutputStream(new File(folder, filename))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, file));
        } catch (java.io.IOException error) { throw new AssertionError(error); }
        finally { image.recycle(); }
    }
}
