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
    static final String EXTRA_OPEN_TAB = "open_tab";
    private BottomNavigationView navigation;
    private NestedScrollView scroll;
    private HomeUi ui;
    private int selected = R.id.nav_home;
    private int[] scrollPositions = {0, 0, 0};
    private OnBackPressedCallback backToHome;
    private LaunchWelcome launchWelcome; // 原生票券欢迎层，展示结束后移除，不新增返回栈页面。
    // 读取本机资料的工作线程，不阻塞界面。
    private final java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean checkingProfile, openingProfile;
    private boolean generating;
    private boolean profileComplete, profileChecked;

    @Override protected void onCreate(Bundle savedInstanceState) {
        // 系统窗口只显示奶白底；票券由应用内同一套布局绘制，避免两次尺寸跳变。
        setTheme(R.style.Theme_Odyssey);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        navigation = findViewById(R.id.bottomNavigation);
        // 生成结束时刷新，提示消失、新行程出现。
        LivePlannerModel live = LivePlannerModel.get(getApplication());
        live.updates.observe(this, ignored -> {
            boolean now = live.busy && live.generationUncertain;
            if (now != generating) { generating = now; if (ui != null) render(); }
        });
        scroll = findViewById(R.id.homeScroll);
        ui = new HomeUi(findViewById(R.id.homeContent));
        if (savedInstanceState != null) {
            selected = savedInstanceState.getInt("home_tab", R.id.nav_home);
            int[] positions = savedInstanceState.getIntArray("home_scroll");
            if (positions != null && positions.length == 3) scrollPositions = positions;
        } else if (getIntent().getBooleanExtra(EXTRA_OPEN_MY, false)) selected = R.id.nav_my;
        else selected = getIntent().getIntExtra(EXTRA_OPEN_TAB, R.id.nav_home);
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
    }

    @Override protected void onNewIntent(Intent intent) {
        // 生成页用 CLEAR_TOP/SINGLE_TOP 复用已有首页时，新参数从这里接收而不是 onCreate。
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_OPEN_MY, false) || intent.hasExtra(EXTRA_OPEN_TAB)) {
            if (launchWelcome != null) launchWelcome.dismiss();
            navigation.setSelectedItemId(intent.getIntExtra(EXTRA_OPEN_TAB, R.id.nav_my));
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
        if (launchWelcome != null) {
            // 首次资料页已接手同一票券；首页不能在返回时重播。
            if (openingProfile) launchWelcome.dismiss(); else launchWelcome.pause();
        }
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (launchWelcome != null) launchWelcome.dismiss(); // 清理延迟任务，避免持有旧 Activity。
        worker.shutdown(); // 已开始的读取可完成，但不再接受新任务。
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
        AppFonts.style(ui.text(getString(R.string.editorial_wordmark), 30, true), android.graphics.Typeface.BOLD);
        generationStatus();
        View banner = LayoutInflater.from(this).inflate(R.layout.home_banner, ui.content, false);
        banner.setOnClickListener(view -> startPlanner());
        ui.content.addView(banner);
        ui.text(getString(R.string.editorial_inspiration), 23, true).setPadding(0, ui.dp(28), 0, 0);
        ui.text(getString(R.string.editorial_inspiration_subtitle), 12, false).setPadding(0, ui.dp(2), 0, ui.dp(12));
        String[] titles = {"A slower kind\nof Kyoto.", "Tokyo, a fresh\nperspective.", "Bali, on\nisland time."};
        String[] keywords = {"Temples · Tea · Slow travel", "Design · Food · Nightlife", "Beaches · Nature · Relaxation"};
        List<SavedTrip> samples = SampleTrips.all(this);
        for (int i = 0; i < samples.size(); i++) {
            SavedTrip trip = samples.get(i);
            TripCardView.addSample(ui, trip, titles[i], keywords[i], () -> startActivity(new Intent(this, TripDetailActivity.class)
                    .putExtra(TripDetailActivity.EXTRA_ID, trip.id).putExtra(TripDetailActivity.EXTRA_SAMPLE, true))).setTag("sample-" + trip.id);
        }
    }
    private void myTrips() {
        ui.text(getString(R.string.editorial_my_title), 28, true);
        generationStatus();
        new HomeUi(ui.group()).actionRow(getString(R.string.profile_edit), "", () -> startActivity(new Intent(this, RegistrationActivity.class)
                .putExtra(RegistrationActivity.EXTRA_EDIT_PROFILE, true))).setId(R.id.editProfile);
        try {
            List<SavedTrip> trips = new TripStore(this).list();
            if (trips.isEmpty()) {
                ui.card(getString(R.string.my_empty_title), getString(R.string.my_empty_description),
                        R.color.surface, null).setId(R.id.myEmptyState);
                ui.button(R.string.create_my_trip, this::startPlanner);
            } else {
                for (SavedTrip trip : trips) {
                    TripCardView.add(ui, trip, () -> openTrip(trip.id))
                            .setTag("saved-" + trip.id);
                }
            }
        } catch (JSONException | ClassCastException exception) {
            ui.text(getString(R.string.trip_read_error), 14, false);
            ui.button(R.string.retry, this::render);
        }
        ui.stamps();
    }
    /** 行程还在后台生成时给出提示；后端没有进度推送，只能告诉用户正在进行。 */
    private void generationStatus() {
        LivePlannerModel model = LivePlannerModel.get(getApplication());
        if (model.busy && model.generationUncertain) ui.card("Your trip is being built", "Usually takes 2–4 minutes. We’ll add it to My Trips when it’s ready.",
                R.color.purple_light, () -> startActivity(new Intent(this, LivePlannerActivity.class)));
    }
    private void startPlanner() {
        LivePlannerModel model = LivePlannerModel.get(getApplication());
        Intent planner = new Intent(this, LivePlannerActivity.class);
        if (!model.busy && model.hasProgress()) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("You have a trip in progress")
                    .setMessage("Pick up where you left off, or start a new trip?")
                    .setNegativeButton("Start over", (d, w) -> startActivity(planner.putExtra(LivePlannerActivity.EXTRA_START_OVER, true)))
                    .setPositiveButton("Continue", (d, w) -> startActivity(planner)).show();
        } else startActivity(planner.putExtra(LivePlannerActivity.EXTRA_NEW_PLAN, true));
    }
    /** 首页保持桌面入口；资料检查完成后由正确页面接手欢迎层。 */
    private void checkProfile() {
        if (checkingProfile) return;
        checkingProfile = true;
        enableTree(findViewById(R.id.homeRoot), false);
        worker.execute(() -> {
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
    /** 只在前台交接，避免后台异步读取触发跳转。 */
    private void openProfileIfNeeded() {
        if (!profileChecked || profileComplete || openingProfile || isFinishing() || isDestroyed()
                || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) return;
        openingProfile = true;
        Intent profile = new Intent(this, RegistrationActivity.class);
        if (launchWelcome != null && launchWelcome.isShowing()) {
            // 不先移除票券露出首页：下一页用同尺寸票券覆盖，接续剩余计时再上滑。
            profile.putExtra(RegistrationActivity.EXTRA_WELCOME_REMAINING, launchWelcome.remainingMillis());
            startActivity(profile, android.app.ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle());
        } else startActivity(profile);
    }
    private void enableTree(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) enableTree(group.getChildAt(i), enabled);
        }
    }
    private void openTrip(String id) {
        // 页面间只传 ID，不把整份大 JSON 塞进 Intent；详情页按 ID 读取已保存的行程。
        startActivity(new Intent(this, TripDetailActivity.class).putExtra(TripDetailActivity.EXTRA_ID, id));
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
