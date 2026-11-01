package com.example.odyssey;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** 只负责展示 LivePlannerModel：逐步对话 → 确认后端提议 → 生成并保存行程。 */
public class LivePlannerActivity extends AppCompatActivity {
    private static final int[] SEGMENTS = {R.id.progressStep1, R.id.progressStep2, R.id.progressStep3, R.id.progressStep4,
            R.id.progressStep5, R.id.progressStep6, R.id.progressStep7, R.id.progressStep8};
    private static final String[] HINTS = {"Your departure city and country.", "Your destination city and country.",
            "Pick your departure and return dates.", "How many adults, and whether any children are travelling.",
            "Currency, amount, whether it covers everyone or per person, the whole trip or per day, and whether flights are included.",
            "Anything essential or preferred. You can also say there are no extra requirements.",
            "How busy you want each day to be.", "Food, culture, nature or anything else you want to experience."};
    private LivePlannerModel model;
    private HomeUi ui;
    private AppCompatEditText input;
    private NestedScrollView scroll;
    private final Calendar month = Calendar.getInstance();
    private int shownStep = -1;
    private final androidx.activity.result.ActivityResultLauncher<String> localNetwork = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) connect();
                else { model.error = "Odyssey needs the Local network permission to reach this server. Allow it and try again."; render(); }
            });

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); EdgeToEdge.enable(this); setContentView(R.layout.activity_live_planner);
        model = LivePlannerModel.get(getApplication());
        ui = new HomeUi(findViewById(R.id.liveContent)); input = findViewById(R.id.liveInput); scroll = findViewById(R.id.liveScroll);
        month.set(Calendar.DAY_OF_MONTH, 1);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            // 标签栏显示时由它自己避开系统导航条，这里只需让出键盘。
            boolean tabs = findViewById(R.id.liveNavigationBar).getVisibility() == View.VISIBLE;
            view.setPadding(bars.left, bars.top, bars.right, tabs ? insets.getInsets(WindowInsetsCompat.Type.ime()).bottom : bars.bottom);
            return insets;
        });
        findViewById(R.id.liveBack).setOnClickListener(view -> back());
        findViewById(R.id.liveNext).setOnClickListener(view -> model.select(model.selected + 1));
        findViewById(R.id.liveSend).setOnClickListener(view -> {
            model.draft(input.getText() == null ? "" : input.getText().toString()); model.send(model.draft());
        });
        input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { model.draft(s.toString()); }
            public void afterTextChanged(android.text.Editable text) { }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { back(); }
        });
        BottomNavigationView tabs = findViewById(R.id.liveNavigation);
        // 行程刚存入 My Trips，所以高亮它；点任何一项（包括已高亮的）都离开本页。
        tabs.setSelectedItemId(R.id.nav_my);
        tabs.setOnItemSelectedListener(item -> { openHomeTab(item.getItemId()); return false; });
        tabs.setOnItemReselectedListener(item -> openHomeTab(item.getItemId()));
        model.updates.observe(this, ignored -> render());
        if (state == null && !model.busy && model.savedTripId.isEmpty() && !model.generationUncertain) {
            connect();
        }
    }
    /** 打开即连接后端；已有会话时只读恢复，不会自动提交消息或确认提议。 */
    private void connect() {
        String permission = "android.permission.ACCESS_LOCAL_NETWORK";
        if (android.os.Build.VERSION.SDK_INT >= 37 && BackendConfig.isLocalNetwork(model.endpoint)
                && ContextCompat.checkSelfPermission(this, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            localNetwork.launch(permission); return;
        }
        if (model.session != null) model.restore(); else model.start();
    }
    private void back() {
        if (!model.savedTripId.isEmpty()) openHomeTab(R.id.nav_home);
        else if (model.session != null && model.selected > 0 && !model.busy) model.select(model.selected - 1);
        else finish();
    }
    private void openHomeTab(int tab) {
        startActivity(new Intent(this, HomeActivity.class).putExtra(HomeActivity.EXTRA_OPEN_TAB, tab)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)); finish();
    }
    private void render() {
        ui.content.removeAllViews();
        boolean saved = !model.savedTripId.isEmpty();
        boolean generating = model.busy && model.generationUncertain;
        boolean planning = model.session != null && !saved && !generating;
        try {
            renderChrome(saved, generating, planning);
            if (!model.error.isEmpty()) ui.card("Update", model.error, R.color.peach, null);
            if (saved) renderSaved();
            else if (generating) renderGenerating();
            else if (planning) renderPlanning();
            else if (model.busy) progress(model.status);
            else if (!model.generationUncertain) {
                button("Try again", R.color.lime, this::connect, "live-start");
                button("Server settings", R.color.surface, this::openSettings, "live-settings");
            }
        } catch (JSONException exception) {
            ui.text("This plan can’t be displayed. Check that the app and server versions match.", 15, false);
        }
        renderRecovery();
    }
    /** 顶部导航、进度、行程卡和输入框这些固定区域。 */
    private void renderChrome(boolean saved, boolean generating, boolean planning) throws JSONException {
        JSONObject steps = model.session == null ? null : model.session.getJSONObject("steps");
        int confirmed = 0;
        if (steps != null) for (String id : LivePlanningProtocol.STEPS)
            if ("CONFIRMED".equals(steps.getJSONObject(id).getString("state"))) confirmed++;
        for (int i = 0; i < SEGMENTS.length; i++) findViewById(SEGMENTS[i]).setBackgroundColor(ContextCompat.getColor(this,
                saved || generating || i < confirmed ? R.color.lime : R.color.outline));
        text(R.id.liveStep, saved ? "Trip generated" : generating ? "Generating" : planning ? (model.selected + 1) + "/8" : "Odyssey");
        findViewById(R.id.liveBack).setEnabled(true);
        findViewById(R.id.liveBack).setVisibility(saved ? View.GONE : View.VISIBLE);
        View next = findViewById(R.id.liveNext);
        next.setVisibility(saved ? View.GONE : planning ? View.VISIBLE : View.INVISIBLE);
        next.setEnabled(planning && !model.busy && model.selected < 7
                && "CONFIRMED".equals(steps.getJSONObject(LivePlanningProtocol.STEPS[model.selected]).getString("state")));
        View tabBar = findViewById(R.id.liveNavigationBar);
        int tabVisibility = saved ? View.VISIBLE : View.GONE;
        if (tabBar.getVisibility() != tabVisibility) { tabBar.setVisibility(tabVisibility); ViewCompat.requestApplyInsets(scroll); }
        renderBoard(steps);
        text(R.id.liveHeadline, saved ? "Your trip, taking shape." : generating ? "Putting your trip together…" : planning ? headline()
                : model.busy ? "Getting things ready…" : model.generationUncertain ? "Let’s find your trip" : "Can’t reach the server");
        boolean editable = planning && !model.busy && model.pendingTurn == null && !model.generationUncertain;
        findViewById(R.id.liveComposer).setVisibility(planning ? View.VISIBLE : View.GONE);
        input.setEnabled(editable); findViewById(R.id.liveSend).setEnabled(editable);
        if (shownStep != model.selected || !model.busy) {
            if (shownStep != model.selected) scroll.post(() -> scroll.scrollTo(0, 0));
            shownStep = model.selected; String draft = model.draft();
            if (input.getText() == null || !input.getText().toString().equals(draft)) { input.setText(draft); input.setSelection(input.length()); }
        }
    }
    private String headline() throws JSONException {
        boolean current = model.selected == LivePlanningProtocol.index(model.session.getString("currentStep"));
        String question = model.session.optString("question");
        return current && !question.isEmpty() ? question : LivePlanningProtocol.TITLES[model.selected];
    }
    private void renderBoard(JSONObject steps) throws JSONException {
        String origin = steps == null ? "" : LivePlanningProtocol.place(steps.getJSONObject("ORIGIN").optJSONObject("answer"));
        String destination = steps == null ? "" : LivePlanningProtocol.place(steps.getJSONObject("DESTINATION").optJSONObject("answer"));
        findViewById(R.id.tripBoard).setVisibility(origin.isEmpty() ? View.GONE : View.VISIBLE);
        if (origin.isEmpty()) return;
        text(R.id.tripBoardDestination, destination.isEmpty() ? "Depart from " + origin : origin + " → " + destination);
        android.widget.ImageView photo = findViewById(R.id.tripBoardPhoto);
        boolean hasPhoto = TripPhotos.resource(destination) != R.drawable.bg_photo_placeholder;
        photo.setVisibility(hasPhoto ? View.VISIBLE : View.GONE);
        if (hasPhoto) TripPhotos.show(photo, destination);
        // 这款字体的空格很窄，箭头两侧用双空格才看得出间隔。
        String dates = LivePlanningProtocol.dates(steps.getJSONObject("DATES").optJSONObject("answer"));
        findViewById(R.id.tripBoardStatus).setVisibility(dates.isEmpty() ? View.GONE : View.VISIBLE);
        text(R.id.tripBoardStatus, dates);
    }
    private void renderPlanning() throws JSONException {
        JSONObject session = model.session, steps = session.getJSONObject("steps");
        String step = LivePlanningProtocol.STEPS[model.selected];
        JSONObject current = steps.getJSONObject(step);
        JSONObject proposal = session.optJSONObject("proposal");
        boolean proposing = proposal != null && step.equals(proposal.getString("stepId"));
        JSONArray messages = current.getJSONArray("messages");
        JSONObject answer = current.optJSONObject("answer");
        if (answer != null && !proposing) ui.card("Your answer", LivePlanningProtocol.answer(step, answer), R.color.purple, null);
        if (model.selected == 2 && !proposing) renderCalendar();
        else if (answer == null && !proposing && messages.length() == 0) ui.text(HINTS[model.selected], 14, false);
        if (messages.length() == 0 && model.selected == LivePlanningProtocol.index(session.getString("currentStep"))
                && !session.optString("assistantMessage").isEmpty()) bubble(session.getString("assistantMessage"), false);
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.getJSONObject(i);
            bubble(message.getString("text"), message.getString("role").equals("USER"));
        }
        if (model.busy) progress(model.status);
        if (proposing) {
            JSONArray options = proposal.getJSONArray("options");
            for (int i = 0; i < options.length(); i++) {
                JSONObject option = options.getJSONObject(i); String id = option.getString("id");
                ui.card(option.getString("title"), LivePlanningProtocol.answer(step, option.getJSONObject("answer")), R.color.surface, null);
                button("Confirm this answer", R.color.lime, () -> model.confirm(id), "live-confirm-" + i)
                        .setEnabled(!model.busy && model.pendingTurn == null && !model.generationUncertain);
            }
        }
        JSONObject ready = session.getJSONObject("generationReadiness");
        if (ready.getBoolean("ready")) renderReview(steps);
        else if (model.selected == 7 && ready.getJSONArray("blockingFields").length() > 0) renderBlocking(ready.getJSONArray("blockingFields"));
        if (model.selected > 0 || answer != null) button("Start a new plan", R.color.surface, model::newSession, "live-fresh");
    }
    private void renderCalendar() {
        ui.content.addView(new TripCalendarView(this, model.dates, month, date -> { model.dates.select(date); render(); }, this::render));
        if (model.dates.isComplete()) {
            int duration = TripDates.daysBetweenInclusive(model.dates.getStartDate(), model.dates.getEndDate());
            if (duration > 60) ui.text("Choose a trip of at most 60 days.", 14, false);
            else button("Confirm dates", R.color.lime, () -> model.send("Fixed dates: depart " + LivePlanningProtocol.isoDate(model.dates.getStartDate())
                    + " and leave the destination on " + LivePlanningProtocol.isoDate(model.dates.getEndDate()) + "."), "live-dates");
        }
    }
    private void renderReview(JSONObject steps) throws JSONException {
        HomeUi review = new HomeUi(ui.group());
        review.content.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8));
        for (String id : LivePlanningProtocol.STEPS) {
            review.text(LivePlanningProtocol.TITLES[LivePlanningProtocol.index(id)], 14, true).setPadding(0, ui.dp(8), 0, ui.dp(2));
            review.text(LivePlanningProtocol.answer(id, steps.getJSONObject(id).getJSONObject("answer")), 14, false).setPadding(0, 0, 0, ui.dp(8));
        }
        button("Generate my trip", R.color.lime, model::generate, "live-generate");
    }
    private void renderBlocking(JSONArray blocking) throws JSONException {
        List<String> reasons = new ArrayList<>();
        for (int i = 0; i < blocking.length(); i++) {
            String reason = blocking.getString(i);
            try { reason = LivePlanningProtocol.TITLES[LivePlanningProtocol.index(reason)]; }
            catch (IllegalArgumentException message) { /* 后端给的是可读的限制说明，直接显示。 */ }
            reasons.add(reason);
        }
        ui.card("Before generating", android.text.TextUtils.join(" · ", reasons), R.color.peach, null);
    }
    private void renderGenerating() {
        ui.content.addView(new GenerationRouteView(this), new LinearLayout.LayoutParams(-1, ui.dp(42)));
        progress("Usually takes a few minutes");
        ui.text(model.status, 19, true);
        button("Return to home", R.color.purple, this::finish, "live-home");
    }
    private void renderSaved() throws JSONException {
        JSONObject steps = model.session == null ? null : model.session.getJSONObject("steps");
        if (steps != null && steps.getJSONObject("COMPANIONS").optJSONObject("answer") != null
                && steps.getJSONObject("BUDGET").optJSONObject("answer") != null) {
            JSONObject budget = steps.getJSONObject("BUDGET").getJSONObject("answer");
            TripBriefView.add(ui.content, LivePlanningProtocol.companions(steps.getJSONObject("COMPANIONS").getJSONObject("answer")),
                    LivePlanningProtocol.budgetTitle(budget), LivePlanningProtocol.budgetCaption(budget), LivePlanningProtocol.preferences(steps));
        }
        button("Open itinerary", R.color.lime, () -> startActivity(new Intent(this, TripDetailActivity.class)
                .putExtra(TripDetailActivity.EXTRA_ID, model.savedTripId)), "live-open");
        LinearLayout row = new LinearLayout(this);
        ui.content.addView(row, new LinearLayout.LayoutParams(-1, -2));
        rowButton(row, "View My Trips", () -> openHomeTab(R.id.nav_my), "live-my-trips", 0);
        rowButton(row, "Plan another trip", model::newSession, "live-fresh", ui.dp(10));
    }
    /** 回复或生成结果不确定时的找回操作；不会自动重发，避免重复提交和重复消耗额度。 */
    private void renderRecovery() {
        if (model.pendingTurn != null && !model.busy) {
            ui.card("Your last reply didn’t come back", "Check it first so nothing is sent twice.", R.color.peach, null);
            button("Check the reply", R.color.lime, model::replay, "live-replay");
            button("Go back to my answers", R.color.surface, model::abandonTurn, "live-abandon");
        }
        if (model.generationUncertain && !model.busy) {
            ui.card("We lost track of your trip", "It may still be generating or already saved. Check your saved trips before trying again.", R.color.peach, null);
            button("Check saved trips", R.color.lime, model::listTrips, "live-recover");
            if (!model.resultJson.isEmpty()) button("Save the itinerary again", R.color.lime, model::retrySave, "live-retry-save");
            button("Generate again", R.color.surface, () -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Generate again?").setMessage("The earlier request may still finish, so you could end up with two copies of this trip.")
                    .setNegativeButton("Keep checking", null).setPositiveButton("Generate again", (d, w) -> model.allowAnotherGeneration()).show(), "live-new-attempt");
        }
        for (int i = 0; i < model.serverTrips.length(); i++) {
            JSONObject trip = model.serverTrips.optJSONObject(i);
            if (trip == null) continue;
            String id = trip.optString("id");
            ui.card(trip.optString("title", "Saved trip"), trip.optString("created_at"), R.color.surface, model.busy ? null : () -> model.recover(id));
        }
    }
    private void openSettings() {
        new BackendSettingsDialog(this, () -> { finish(); startActivity(new Intent(this, LivePlannerActivity.class)); }).show();
    }
    private void progress(String label) {
        LinearProgressIndicator progress = new LinearProgressIndicator(this);
        progress.setIndeterminate(true); progress.setIndicatorColor(ui.color(R.color.purple)); progress.setTrackColor(ui.color(R.color.outline));
        LinearLayout.LayoutParams track = new LinearLayout.LayoutParams(-1, ui.dp(6)); track.setMargins(0, ui.dp(8), 0, ui.dp(8));
        ui.content.addView(progress, track);
        if (!label.isEmpty()) ui.text(label, 14, false);
    }
    private void bubble(String value, boolean user) {
        TextView bubble = new TextView(this); bubble.setText(value); bubble.setTextSize(14);
        AppFonts.style(bubble, android.graphics.Typeface.NORMAL);
        bubble.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        bubble.setTextColor(ui.color(user ? R.color.text_primary : R.color.on_accent));
        bubble.setBackgroundResource(user ? R.drawable.bg_user_chat : R.drawable.bg_chat_bubble);
        LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-1, -2); row.setMargins(0, ui.dp(4), 0, ui.dp(4));
        ui.content.addView(bubble, row);
    }
    private MaterialButton button(String text, int background, Runnable click, String tag) {
        MaterialButton button = ui.button(text, click);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ui.color(background)));
        if (background == R.color.surface) button.setTextColor(ui.color(R.color.text_primary));
        button.setTag(tag); button.setEnabled(!model.busy || tag.equals("live-home"));
        return button;
    }
    private void rowButton(LinearLayout row, String text, Runnable click, String tag, int margin) {
        MaterialButton button = button(text, R.color.surface, click, tag);
        ui.content.removeView(button);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f); params.setMarginStart(margin);
        row.addView(button, params);
    }
    private void text(int id, String value) { ((TextView) findViewById(id)).setText(value); }
}
