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
 * 首页或首次资料页上的临时欢迎层，不是另一张 Activity，因此返回键不会回到启动票券。
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
    private boolean revealing; // 上滑阶段只播放一次，旋转或切后台时直接收尾。
    boolean isShowing() { return overlay != null; }

    // Runnable 是要稍后执行的任务；保存同一个实例，暂停时才能准确取消。
    private final Runnable finish = this::reveal;
    private final Runnable begin = this::beginCountdown;

    private void beginCountdown() {
        if (overlay == null) return;
        countdown.resume(SystemClock.uptimeMillis());
        overlay.postDelayed(finish, countdown.remaining(SystemClock.uptimeMillis()));
    }

    LaunchWelcome(HomeActivity activity, Bundle savedState) {
        this(activity, savedState, R.id.homeRoot, -1);
    }

    /** 首页和首次资料页使用相同的票券布局、窗口坐标和剩余时间，交接时不缩放。 */
    LaunchWelcome(androidx.appcompat.app.AppCompatActivity activity, Bundle savedState, int contentId, long transferredMillis) {
        home = activity.findViewById(contentId);
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
        long remaining = savedState == null ? (transferredMillis >= 0 ? transferredMillis : (launcher ? duration : 0))
                : savedState.getLong(STATE_REMAINING, 0);
        countdown = new LaunchCountdown(remaining);
        if (remaining == 0) return; // 已看完或内部进入时，不创建票券控件。

        overlay = activity.getLayoutInflater().inflate(R.layout.activity_launch, container, false);
        if (Build.VERSION.SDK_INT >= 29) overlay.setForceDarkAllowed(false);
        container.addView(overlay); // 最后添加的子 View 位于原首页上方。
        home.setVisibility(View.INVISIBLE); // 保留首页布局，但阻止误点和读屏访问底层内容。
        bars.setAppearanceLightStatusBars(true); // 奶白色欢迎页使用深色系统图标。
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

    /** 跨 Activity 交接只传剩余展示时间，不把 1.5 秒计时重新开始。 */
    long remainingMillis() {
        return overlay == null ? 0 : Math.max(1, countdown.remaining(SystemClock.uptimeMillis()));
    }

    void resume() {
        if (overlay == null || revealing) return;
        // 随下一帧开始计时，不把 Activity 创建和首页初始化时间算进展示时长。
        overlay.removeCallbacks(begin);
        overlay.postOnAnimation(begin);
    }

    void pause() {
        if (overlay == null) return;
        if (revealing) { dismiss(); return; }
        overlay.removeCallbacks(begin);
        overlay.removeCallbacks(finish);
        countdown.pause(SystemClock.uptimeMillis()); // 切后台时不继续倒计时，也不跳转。
    }

    void save(Bundle state) {
        // 剩余为零也保存，确保旋转或主题重建后不会再显示欢迎页。
        state.putLong(STATE_REMAINING, overlay == null || revealing ? 0
                : countdown.remaining(SystemClock.uptimeMillis()));
    }

    /** 票券不做缩放，仅随奶白欢迎层上滑；下面的真实表单从屏幕底部同步上移进入。 */
    private void reveal() {
        if (overlay == null || revealing) return;
        if (!overlay.isLaidOut()) { overlay.postOnAnimation(finish); return; }
        if (Build.VERSION.SDK_INT >= 26 && !android.animation.ValueAnimator.areAnimatorsEnabled()) {
            dismiss(); return; // 尊重系统“移除动画”，直接显示可操作的内容。
        }
        revealing = true;
        home.setVisibility(View.VISIBLE);
        // 两层移动距离和曲线一致：先露出表单标题，再露出字段，避免先看到被截断的下半页。
        home.setAlpha(1f);
        home.setTranslationY(overlay.getHeight());
        home.animate().translationY(0f).setStartDelay(0).setDuration(900)
                .setInterpolator(new android.view.animation.PathInterpolator(0.42f, 0f, 0.58f, 1f)).start();
        overlay.animate().translationY(-overlay.getHeight()).setDuration(900)
                .setInterpolator(new android.view.animation.PathInterpolator(0.42f, 0f, 0.58f, 1f))
                .withEndAction(this::dismiss).start();
    }

    void dismiss() {
        if (overlay == null) return; // 防止生命周期回调或定时任务重复移除。
        overlay.removeCallbacks(begin);
        overlay.removeCallbacks(finish);
        overlay.animate().withEndAction(null).cancel();
        home.animate().cancel();
        countdown.pause(SystemClock.uptimeMillis());
        revealing = false;
        container.removeView(overlay);
        overlay = null;
        home.setVisibility(View.VISIBLE);
        home.setAlpha(1f); home.setTranslationY(0f);
        bars.setAppearanceLightStatusBars(previousLightStatus);
        bars.setAppearanceLightNavigationBars(previousLightNavigation);
    }
}
