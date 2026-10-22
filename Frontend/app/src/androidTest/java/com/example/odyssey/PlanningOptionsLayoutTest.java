package com.example.odyssey;

import android.content.Context;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.card.MaterialCardView;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Calendar;
import static org.junit.Assert.*;

/** 紧凑选项保持 48dp 触控，只有标题加粗表达选中，浅/深色与大字体均不使用勾选或底色。 */
@RunWith(AndroidJUnit4.class)
public class PlanningOptionsLayoutTest {
    @org.junit.Rule public final ProfileTestFixture profile = new ProfileTestFixture();
    @Test public void compactOptionsUseBoldOnlyAndKeepAccessibleTouchTargets() {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int width : new int[]{320, 400}) for (float scale : new float[]{1, 2})
                for (boolean dark : new boolean[]{false, true}) {
                    Configuration config = new Configuration(base.getResources().getConfiguration());
                    config.screenWidthDp = width; config.fontScale = scale;
                    config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                            | (dark ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
                    Context context = new ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_Odyssey);
                    LinearLayout root = new LinearLayout(context); root.setOrientation(LinearLayout.VERTICAL);
                    PlanningSession session = new PlanningSession("Sydney", ""); session.current = 3; session.toggle("friends");
                    PlanningSessionRenderer renderer = new PlanningSessionRenderer(root, new PlanningSessionRenderer.Actions() {
                        public void option(String id) { } public void confirm() { } public void date(int value) { } public void redraw() { }
                    });
                    renderer.render(session, Calendar.getInstance(), false); layout(root, width);
                    TextView friends = (TextView) PlannerFlowTest.findText(root, "Friends");
                    TextView solo = (TextView) PlannerFlowTest.findText(root, "Solo");
                    assertNotNull("Selection must preserve the plain title without a tick", friends);
                    assertNotNull(solo); assertTrue(isBold(friends)); assertFalse(isBold(solo));
                    TextView description = (TextView) PlannerFlowTest.findText(root, "Travel with friends");
                    assertNotNull(description); assertFalse("Captions always keep normal weight", isBold(description));
                    MaterialCardView selected = card(friends), normal = card(solo);
                    assertTrue("Selected state stays available to accessibility", selected.isSelected());
                    assertFalse(normal.isSelected());
                    assertEquals(ContextCompat.getColor(context, R.color.surface), selected.getCardBackgroundColor().getDefaultColor());
                    assertEquals(normal.getCardBackgroundColor().getDefaultColor(), selected.getCardBackgroundColor().getDefaultColor());
                    assertEquals(normal.getStrokeColor(), selected.getStrokeColor());
                    assertTrue(selected.getHeight() >= dp(context, 48)); assertTrue(normal.getHeight() >= dp(context, 48));
                    if (scale == 1) assertTrue("Compact companion card stays at most 76dp high", selected.getHeight() <= dp(context, 76));
                    assertRenderedTextFits(root);
                    session.current = 1; renderer.render(session, Calendar.getInstance(), false); layout(root, width);
                    ImageView image = image(root); assertNotNull(image);
                    assertEquals("Destination image uses a compact 72dp strip", dp(context, 72), image.getHeight());
                    assertRenderedTextFits(root);
                }
        });
    }
    @Test public void captureCompactDestinationAndSelectedCompanions() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            PlannerFlowTest.choose(scenario, "Sydney", "1/7");
            scenario.onActivity(a -> { PlannerFlowTest.clickText(a, "Tokyo"); showOptions(a); });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            EditorialLayoutTest.saveScreen(context, "compact-destination.png");
            scenario.onActivity(a -> PlannerFlowTest.clickText(a, "Confirm destination"));
            PlannerFlowTest.awaitStep(scenario, "2/7");
            PlannerFlowTest.choose(scenario, "Flexible dates", "3/7");
            scenario.onActivity(a -> {
                PlannerFlowTest.clickText(a, "Friends"); PlannerFlowTest.clickText(a, "Family");
                View title = PlannerFlowTest.findText(a.findViewById(R.id.stepContent), "Friends");
                assertNotNull("Selected title has no tick", title); assertTrue(card(title).isSelected());
                showOptions(a);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            EditorialLayoutTest.saveScreen(context, "compact-companions.png");
        }
    }
    /** 不只检查测量值：绘制每种主题/字号配置，并验证每一行文字没有被挤出可用范围。 */
    private static void assertRenderedTextFits(View root) {
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(root.getWidth(), root.getHeight(),
                android.graphics.Bitmap.Config.ARGB_8888);
        try { root.draw(new android.graphics.Canvas(bitmap)); }
        finally { bitmap.recycle(); }
        assertTextFits(root);
    }
    private static void assertTextFits(View view) {
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
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            assertTextFits(((ViewGroup) view).getChildAt(i));
    }
    private static MaterialCardView card(View view) {
        while (!(view instanceof MaterialCardView) && view.getParent() instanceof View) view = (View) view.getParent();
        assertTrue(view instanceof MaterialCardView); return (MaterialCardView) view;
    }
    // Android 的系统默认 normal 字体可用 null 表示，不能把它当作缺失字体或加粗。
    private static boolean isBold(TextView view) { return view.getTypeface() != null && view.getTypeface().isBold(); }
    private static void showOptions(MainActivity activity) {
        androidx.core.widget.NestedScrollView scroll = activity.findViewById(R.id.main);
        scroll.scrollTo(0, Math.max(0, activity.findViewById(R.id.stepContent).getTop() - dp(activity, 8)));
    }
    private static ImageView image(View view) {
        if (view instanceof ImageView) return (ImageView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            ImageView found = image(((ViewGroup) view).getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
    private static void layout(LinearLayout root, int width) {
        root.measure(View.MeasureSpec.makeMeasureSpec(dp(root.getContext(), width), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
    }
    private static int dp(Context context, int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
