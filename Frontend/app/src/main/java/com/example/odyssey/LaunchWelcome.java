package com.example.odyssey;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * 首页上的临时欢迎层，不是另一张 Activity，因此返回键不会回到启动票券。
 * 图片里的姓名、航线与条码只是品牌示例；没有读取或修改用户真实行程。
 */
final class LaunchWelcome {
    private static final String STATE_REMAINING = "launch_remaining_ms";
    private static final int DURATION_MS = 1500;
    private final View home;
    private final ViewGroup container;
    private final WindowInsetsControllerCompat bars;
    private final boolean previousLightStatus;
    private final boolean previousLightNavigation;
    private final LaunchCountdown countdown;
    private View overlay;
    private Runnable onDismiss;
    void onDismiss(Runnable action) { onDismiss = action; }
    boolean isShowing() { return overlay != null; }

    // Runnable 是要稍后执行的任务；保存同一个实例，暂停时才能准确取消。
    private final Runnable finish = this::dismiss;
    private final Runnable begin = this::beginCountdown;

    private void beginCountdown() {
        if (overlay == null) return;
        countdown.resume(SystemClock.uptimeMillis());
        overlay.postDelayed(finish, countdown.remaining(SystemClock.uptimeMillis()));
    }

    LaunchWelcome(HomeActivity activity, Bundle savedState) {
        home = activity.findViewById(R.id.homeRoot);
        container = activity.findViewById(android.R.id.content);
        bars = WindowCompat.getInsetsController(activity.getWindow(), container);
        previousLightStatus = bars.isAppearanceLightStatusBars();
        previousLightNavigation = bars.isAppearanceLightNavigationBars();

        // 只有点击桌面图标的新启动需要展示；应用内部返回 My Trips 等入口直接显示首页。
        Intent intent = activity.getIntent();
        boolean launcher = Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
                && !intent.getBooleanExtra(HomeActivity.EXTRA_OPEN_MY, false);
        int duration = DURATION_MS;
        if (Build.VERSION.SDK_INT >= 29) {
            AccessibilityManager accessibility = activity.getSystemService(AccessibilityManager.class);
            // 普通设置为 1.5 秒；用户要求更长阅读时间时尊重系统无障碍超时设置。
            if (accessibility != null) duration = accessibility.getRecommendedTimeoutMillis(
                    duration, AccessibilityManager.FLAG_CONTENT_TEXT);
        }
        long remaining = savedState == null ? (launcher ? duration : 0)
                : savedState.getLong(STATE_REMAINING, 0);
        countdown = new LaunchCountdown(remaining);
        if (remaining == 0) return; // 已看完或内部进入时，不创建票券控件。

        overlay = activity.getLayoutInflater().inflate(R.layout.activity_launch, container, false);
        if (Build.VERSION.SDK_INT >= 29) overlay.setForceDarkAllowed(false);
        container.addView(overlay); // 最后添加的子 View 位于原首页上方。
        home.setVisibility(View.INVISIBLE); // 保留首页布局，但阻止误点和读屏访问底层内容。
        bars.setAppearanceLightStatusBars(true); // 浅紫色欢迎页使用深色系统图标。
        bars.setAppearanceLightNavigationBars(true);
        int margin = Math.round(16 * activity.getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(overlay, (view, insets) -> {
            Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(margin + safe.left, margin + safe.top,
                    margin + safe.right, margin + safe.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(overlay);
    }

    void resume() {
        if (overlay == null) return;
        // 随下一帧开始计时，不把 Activity 创建和首页初始化时间算进展示时长。
        overlay.removeCallbacks(begin);
        overlay.postOnAnimation(begin);
    }

    void pause() {
        if (overlay == null) return;
        overlay.removeCallbacks(begin);
        overlay.removeCallbacks(finish);
        countdown.pause(SystemClock.uptimeMillis()); // 切后台时不继续倒计时，也不跳转。
    }

    void save(Bundle state) {
        // 剩余为零也保存，确保旋转或主题重建后不会再显示欢迎页。
        state.putLong(STATE_REMAINING, overlay == null ? 0
                : countdown.remaining(SystemClock.uptimeMillis()));
    }

    void dismiss() {
        if (overlay == null) return; // 防止生命周期回调或定时任务重复移除。
        pause();
        container.removeView(overlay);
        overlay = null;
        home.setVisibility(View.VISIBLE);
        bars.setAppearanceLightStatusBars(previousLightStatus);
        bars.setAppearanceLightNavigationBars(previousLightNavigation);
        if (onDismiss != null) onDismiss.run();
    }
}
