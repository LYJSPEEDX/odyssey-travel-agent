package com.example.odyssey;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.json.JSONException;
import java.util.List;

/**
 * 应用首页容器：底部三个 tab 在本 Activity 内切换，五步规划和行程详情是独立 Activity。
 * 每个 tab 分别保存滚动位置；从生成页回来时重新读取行程库，避免继续显示旧的空状态。
 */
public class HomeActivity extends AppCompatActivity {
    static final String EXTRA_OPEN_MY = "open_my";
    private BottomNavigationView navigation;
    private NestedScrollView scroll;
    private HomeUi ui;
    private int selected = R.id.nav_home;
    private int[] scrollPositions = {0, 0, 0};
    private OnBackPressedCallback backToHome;
    private LaunchWelcome launchWelcome; // 原生票券欢迎层，展示结束后移除，不新增返回栈页面。
    // 图片无需网络；这个工作线程只处理用户主动点击的本机示例保存。
    private final java.util.concurrent.ExecutorService sampleWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean savingSample;
    private boolean checkingProfile, openingProfile;
    private boolean profileComplete, profileChecked;

    @Override protected void onCreate(Bundle savedInstanceState) {
        // 系统先读取 Manifest 中的 Launch 主题绘制品牌图标；进入应用后恢复正常浅/深色主题。
        setTheme(R.style.Theme_Odyssey);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        navigation = findViewById(R.id.bottomNavigation);
        scroll = findViewById(R.id.homeScroll);
        ui = new HomeUi(findViewById(R.id.homeContent));
        if (savedInstanceState != null) {
            selected = savedInstanceState.getInt("home_tab", R.id.nav_home);
            int[] positions = savedInstanceState.getIntArray("home_scroll");
            if (positions != null && positions.length == 3) scrollPositions = positions;
        } else if (getIntent().getBooleanExtra(EXTRA_OPEN_MY, false)) selected = R.id.nav_my;
        backToHome = new OnBackPressedCallback(selected != R.id.nav_home) {
            @Override public void handleOnBackPressed() { navigation.setSelectedItemId(R.id.nav_home); }
        };
        getOnBackPressedDispatcher().addCallback(this, backToHome);
        navigation.setOnItemSelectedListener(item -> {
            if (selected != item.getItemId()) scrollPositions[tabIndex(selected)] = scroll.getScrollY();
            selected = item.getItemId();
            render();
            int position = scrollPositions[tabIndex(selected)];
            scroll.post(() -> scroll.scrollTo(0, position));
            return true;
        });
        navigation.setSelectedItemId(selected);
        // BottomNavigationView handles its own bottom system inset.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.homeRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, 0);
            return insets;
        });
        // 首页先准备好，再盖上欢迎层；1.5 秒后揭开时不需要重新启动 Activity。
        launchWelcome = new LaunchWelcome(this, savedInstanceState);
        launchWelcome.onDismiss(this::openProfileIfNeeded);
    }

    @Override protected void onNewIntent(Intent intent) {
        // 生成页用 CLEAR_TOP/SINGLE_TOP 复用已有首页时，新参数从这里接收而不是 onCreate。
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_OPEN_MY, false)) {
            if (launchWelcome != null) launchWelcome.dismiss();
            navigation.setSelectedItemId(R.id.nav_my);
        }
    }
    @Override protected void onResume() {
        super.onResume();
        openingProfile = false;
        profileChecked = false;
        // Returning from generation must refresh the library, not a cached empty state.
        if (ui != null) render();
        if (launchWelcome != null) launchWelcome.resume();
        checkProfile();
    }
    @Override protected void onPause() {
        if (launchWelcome != null) launchWelcome.pause(); // 切到后台停止计时。
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (launchWelcome != null) launchWelcome.dismiss(); // 清理延迟任务，避免持有旧 Activity。
        sampleWorker.shutdown(); // 已开始的短写入可完成，但不再接受新任务。
        super.onDestroy();
    }
    private void render() {
        ui.content.removeAllViews();
        backToHome.setEnabled(selected != R.id.nav_home);
        if (selected == R.id.nav_my) myTrips();
        else if (selected == R.id.nav_settings) new HomeSettings(this, ui, this::render).render();
        else home();
        enableTree(findViewById(R.id.homeRoot), profileComplete);
    }
    private void home() {
        // 品牌栏保持轻量；主要视觉焦点留给下方照片，而不是重复的彩色面板。
        ui.text(getString(R.string.editorial_wordmark), 28, true);
        View banner = LayoutInflater.from(this).inflate(R.layout.home_banner, ui.content, false);
        banner.setOnClickListener(view -> startPlanner());
        ui.content.addView(banner);
        ui.text(getString(R.string.editorial_inspiration), 23, true).setPadding(0, ui.dp(28), 0, ui.dp(8));
        ui.text(getString(R.string.editorial_inspiration_subtitle), 12, false);
        List<SavedTrip> examples = ExampleTrips.all(this);
        for (int i = 0; i < examples.size(); i++) {
            SavedTrip trip = examples.get(i);
            TripCardView.add(ui, trip, true, () -> openTrip(trip.id, true), () -> saveExample(trip.id))
                    .setTag("example-" + trip.id);
        }
        ui.text(getString(R.string.editorial_sample_note), 12, false);
        ui.text(getString(R.string.editorial_photo_credit), 11, false);
    }
    private void myTrips() {
        ui.text(getString(R.string.editorial_wordmark), 28, true);
        ui.text(getString(R.string.editorial_collection), 11, false);
        ui.title(R.string.editorial_my_title, R.string.editorial_my_subtitle);
        ui.button(R.string.profile_edit, () -> startActivity(new Intent(this, RegistrationActivity.class)
                .putExtra(RegistrationActivity.EXTRA_EDIT_PROFILE, true))).setId(R.id.editProfile);
        try {
            List<SavedTrip> trips = new TripStore(this).list();
            if (trips.isEmpty()) {
                ui.card(getString(R.string.my_empty_title), getString(R.string.my_empty_description),
                        R.color.surface, null).setId(R.id.myEmptyState);
                ui.button(R.string.create_my_trip, this::startPlanner);
            } else {
                for (SavedTrip trip : trips) {
                    TripCardView.add(ui, trip, false, () -> openTrip(trip.id, false), null)
                            .setTag("saved-" + trip.id);
                }
            }
        } catch (JSONException | ClassCastException exception) {
            ui.text(getString(R.string.trip_read_error), 14, false);
            ui.button(R.string.retry, this::render);
        }
        ui.text(getString(R.string.local_only_notice), 12, false);
    }
    private void startPlanner() { startActivity(new Intent(this, MainActivity.class)); }
    /** 首页保持桌面入口；欢迎层结束后再打开本机资料门槛，防止重复启动。 */
    private void checkProfile() {
        if (checkingProfile) return;
        checkingProfile = true;
        enableTree(findViewById(R.id.homeRoot), false);
        sampleWorker.execute(() -> {
            boolean complete = false;
            try { complete = ProfileStore.isComplete(new ProfileStore(this).load()); }
            catch (java.io.IOException | JSONException | IllegalArgumentException ignored) { /* 由资料页展示读取错误与重试。 */ }
            final boolean ready = complete;
            runOnUiThread(() -> {
                checkingProfile = false;
                if (isFinishing() || isDestroyed()) return;
                profileComplete = ready; profileChecked = true;
                enableTree(findViewById(R.id.homeRoot), ready);
                openProfileIfNeeded();
            });
        });
    }
    /** 只在前台且欢迎层真正结束后跳转，不持有后台延迟跳转任务。 */
    private void openProfileIfNeeded() {
        if (!profileChecked || profileComplete || openingProfile || isFinishing() || isDestroyed()
                || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
                || (launchWelcome != null && launchWelcome.isShowing())) return;
        openingProfile = true;
        startActivity(new Intent(this, RegistrationActivity.class));
    }
    private void enableTree(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) enableTree(group.getChildAt(i), enabled);
        }
    }
    /** 收藏示例实际保存完整快照，不只是变一下图标颜色；重复点击不会覆盖已有记录。 */
    private void saveExample(String id) {
        if (savingSample) return; // 忽略正在保存期间的重复点击。
        savingSample = true;
        sampleWorker.execute(() -> {
            boolean saved = ExampleTrips.save(getApplicationContext(), id);
            runOnUiThread(() -> {
                savingSample = false;
                if (isFinishing() || isDestroyed()) return; // 旋转后不操作已销毁的页面。
                android.widget.Toast.makeText(this, saved ? R.string.editorial_sample_saved
                        : R.string.trip_save_failed, android.widget.Toast.LENGTH_SHORT).show();
                // 若用户已切到 My Trips，保存后立即刷新；首页不会突然跳页或改变滚动位置。
                if (saved && selected == R.id.nav_my) render();
            });
        });
    }
    private void openTrip(String id, boolean sample) {
        // 页面间只传 ID 和来源，不把整份大 JSON 塞进 Intent；详情页按来源加载内容。
        startActivity(new Intent(this, TripDetailActivity.class)
                .putExtra(TripDetailActivity.EXTRA_ID, id).putExtra(TripDetailActivity.EXTRA_SAMPLE, sample));
    }
    private int tabIndex(int id) { return id == R.id.nav_my ? 1 : id == R.id.nav_settings ? 2 : 0; }
    @Override protected void onSaveInstanceState(Bundle outState) {
        if (launchWelcome != null) launchWelcome.save(outState); // 旋转时保留剩余展示时间。
        scrollPositions[tabIndex(selected)] = scroll.getScrollY();
        outState.putInt("home_tab", selected);
        outState.putIntArray("home_scroll", scrollPositions);
        super.onSaveInstanceState(outState);
    }
}
