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
    /** 预设选项：标题、说明和发给助手的完整句子。 */
    private static final String[][] COMPANIONS = {{"Solo", "Just me", "One adult, no children."}, {"Partner", "Two of us", "Two adults, no children."},
            {"Friends", "A group of friends", "Travelling with friends, adults only."}, {"Family", "Travelling with family", "Travelling with family."}};
    private static final String[][] CONSTRAINTS = {{"Vegetarian", "Meat-free choices"}, {"With pets", "Pet-friendly stays"}, {"With older adults", "More breaks, easier connections"},
            {"Less walking", "Short walking distances"}, {"See more", "Fit more into each day"}, {"Take it slow", "Space to rest"}};
    private static final String[][] PACES = {{"Relaxed", "One main activity, with breaks", "A relaxed pace with regular breaks."},
            {"Balanced", "A mix of exploring and downtime", "A balanced pace."}, {"Packed", "See more each day", "A packed pace, seeing more each day."}};
    private static final String[][] EXPERIENCES = {{"Local discoveries", "Food, neighbourhoods and everyday culture", "Local discoveries: food, neighbourhoods and everyday culture."},
            {"Nature time", "Parks, scenery and time outdoors", "Nature time: parks, scenery and time outdoors."},
            {"Classic highlights", "The memorable sights", "Classic highlights and the memorable sights."}};
    private static final String[] DESTINATIONS = {"Tokyo, Japan", "Bali, Indonesia", "Kyoto, Japan", "Paris, France", "New York, United States"};
    /** 从首页横幅进入：上一趟已生成完时开始新计划。 */
    static final String EXTRA_NEW_PLAN = "new_plan";
    /** 用户在首页选择放弃未完成的计划，从头开始。 */
    static final String EXTRA_START_OVER = "start_over";
    private LivePlannerModel model;
    private HomeUi ui;
    private AppCompatEditText input;
    private NestedScrollView scroll;
    private final Calendar month = Calendar.getInstance();
    private int shownStep = -1;
    private String chosenOption = "", tappedChoice = "";
    private int shownMessages;
    private boolean startOver;
    private final java.util.Set<String> constraints = new java.util.LinkedHashSet<>();
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
        boolean done = !model.savedTripId.isEmpty();
        startOver = state == null && getIntent().getBooleanExtra(EXTRA_START_OVER, false);
        if (state == null && !model.busy && (startOver || (!model.generationUncertain && (!done || getIntent().getBooleanExtra(EXTRA_NEW_PLAN, false))))) connect();
    }
    /** 打开即连接后端；已有会话时只读恢复，不会自动提交消息或确认提议。 */
    private void connect() {
        String permission = "android.permission.ACCESS_LOCAL_NETWORK";
        if (android.os.Build.VERSION.SDK_INT >= 37 && BackendConfig.isLocalNetwork(model.endpoint)
                && ContextCompat.checkSelfPermission(this, permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            localNetwork.launch(permission); return;
        }
        if (startOver) { startOver = false; model.startOver(); }
        else if (!model.savedTripId.isEmpty()) model.newSession();
        else if (model.session != null) model.restore();
        else model.start();
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
        if (!model.busy) tappedChoice = "";
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
        boolean hasPhoto = !destination.isEmpty();
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
        JSONObject ready = session.getJSONObject("generationReadiness");
        if (!ready.getBoolean("ready") && model.selected == 7 && ready.getJSONArray("blockingFields").length() > 0) renderBlocking(ready.getJSONArray("blockingFields"));
        // 点预设或 Skip 后等回复时只显示进度；跳过的步骤之后也不显示这段对话。
        boolean presetTurn = model.busy && !tappedChoice.isEmpty();
        boolean skipped = model.skipped.contains(model.selected) && "CONFIRMED".equals(current.getString("state"));
        if (answer != null && !proposing) ui.card(LivePlanningProtocol.headline(step, answer), LivePlanningProtocol.detail(step, answer), R.color.purple, null);
        // 操作区固定在上面，对话始终在操作区下面。
        if (proposing && !presetTurn) renderProposal(step, proposal);
        else if (answer == null && !proposing) {
            if (model.selected == 2) renderCalendar();
            else renderChoices();
        }
        if (ready.getBoolean("ready")) renderReview(steps);
        boolean isCurrent = model.selected == LivePlanningProtocol.index(session.getString("currentStep"));
        if (!presetTurn && !skipped) {
            if (messages.length() == 0 && isCurrent && !session.optString("assistantMessage").isEmpty()) bubble(session.getString("assistantMessage"), false);
            for (int i = 0; i < messages.length(); i++) {
                JSONObject message = messages.getJSONObject(i);
                bubble(message.getString("text"), message.getString("role").equals("USER"));
            }
        }
        if (model.busy) progress(model.status);
        // 助手追问时操作区可能很高（日历），滚到最新一条；有提议时留在上面看选项。
        int shown = presetTurn || skipped ? 0 : messages.length();
        if (shown > shownMessages && !proposing) scroll.post(() -> scroll.smoothScrollTo(0, scroll.getChildAt(0).getHeight()));
        shownMessages = shown;
    }
    /** 多个提议并列，点击变紫，再用同一个按钮确认；同名结果只留一个。 */
    private void renderProposal(String step, JSONObject proposal) throws JSONException {
        JSONArray options = proposal.getJSONArray("options");
        List<JSONObject> distinct = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < options.length(); i++) {
            JSONObject option = options.getJSONObject(i);
            if (seen.add(LivePlanningProtocol.headline(step, option.getJSONObject("answer")))) distinct.add(option);
        }
        boolean known = false;
        for (JSONObject option : distinct) known |= option.getString("id").equals(chosenOption);
        if (!known) chosenOption = distinct.size() == 1 ? distinct.get(0).getString("id") : "";
        boolean photos = step.equals("DESTINATION");
        for (JSONObject option : distinct) {
            String id = option.getString("id");
            JSONObject answer = option.getJSONObject("answer");
            ui.content.addView(optionCard(LivePlanningProtocol.headline(step, answer), LivePlanningProtocol.detail(step, answer),
                    photos ? LivePlanningProtocol.place(answer) : "", id.equals(chosenOption), () -> { chosenOption = id; render(); }), cardParams());
        }
        // 超过人数上限的提议不能确认，否则要到生成时才失败。
        if (step.equals("COMPANIONS")) for (JSONObject option : distinct)
            if (option.getString("id").equals(chosenOption) && LivePlanningProtocol.tooManyAdults(option.getJSONObject("answer"))) {
                ui.card("Too many travellers", LivePlanningProtocol.ADULT_LIMIT + " Tell us a smaller group.", R.color.peach, null);
                return;
            }
        button("Confirm this answer", R.color.lime, () -> model.confirm(chosenOption), "live-confirm")
                .setEnabled(!chosenOption.isEmpty() && !model.busy && model.pendingTurn == null && !model.generationUncertain);
    }
    /** 每一步的预设选项：点一下就发给助手；最后三步还能跳过。 */
    private void renderChoices() {
        switch (model.selected) {
            case 0:
                for (String place : LivePlanningProtocol.originSuggestions(model.creation)) choice(place, "", "", "Departing from " + place + ".");
                break;
            case 1:
                String origin = "";
                try { origin = LivePlanningProtocol.place(model.session.getJSONObject("steps").getJSONObject("ORIGIN").optJSONObject("answer")); }
                catch (JSONException ignored) { /* 没有出发地时不做排除。 */ }
                for (String place : DESTINATIONS) {
                    String city = place.split(",")[0];
                    if (!city.equalsIgnoreCase(origin)) choice(place, "", city, "Destination " + place + ".");
                }
                break;
            case 3:
                ui.text(LivePlanningProtocol.ADULT_LIMIT, 14, false);
                choices(COMPANIONS);
                break;
            case 4: choices(LivePlanningProtocol.budgetTiers(model.creation)); break;
            case 5: renderConstraints(); break;
            case 6: choices(PACES); skip("A balanced pace."); break;
            case 7: choices(EXPERIENCES); skip("General sightseeing."); break;
            default: break;
        }
    }
    private void renderConstraints() {
        for (int i = 0; i < CONSTRAINTS.length; i += 2) {
            LinearLayout row = new LinearLayout(this); row.setBaselineAligned(false);
            for (int j = i; j < Math.min(i + 2, CONSTRAINTS.length); j++) {
                String name = CONSTRAINTS[j][0];
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1f);
                params.setMargins(j > i ? ui.dp(10) : 0, ui.dp(5), 0, ui.dp(5));
                row.addView(optionCard(name, CONSTRAINTS[j][1], "", constraints.contains(name), () -> {
                    if (!constraints.remove(name)) constraints.add(name);
                    render();
                }), params);
            }
            ui.content.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        button("Continue", R.color.lime, () -> send("Preferred: " + android.text.TextUtils.join(", ", constraints) + "."), "live-continue")
                .setEnabled(!constraints.isEmpty() && !model.busy);
        skip("No extra requirements.");
    }
    private void choices(String[][] items) { for (String[] item : items) choice(item[0], item[1], "", item[2]); }
    private void choice(String title, String detail, String photo, String message) {
        ui.content.addView(optionCard(title, detail, photo, message.equals(tappedChoice), () -> send(message)), cardParams());
    }
    private void skip(String message) {
        button("Skip", R.color.surface, () -> { if (!model.busy) model.skipped.add(model.selected); send(message); }, "live-skip");
    }
    private void send(String message) {
        if (model.busy) return;
        tappedChoice = message; model.choose(message);
    }
    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.setMargins(0, ui.dp(5), 0, ui.dp(5));
        return params;
    }
    private MaterialCardView optionCard(String title, String detail, String photoCity, boolean selected, Runnable click) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(ui.dp(20)); card.setCardElevation(0); card.setMinimumHeight(ui.dp(48));
        card.setCardBackgroundColor(ui.color(selected ? R.color.purple : R.color.surface));
        card.setStrokeColor(ui.color(R.color.on_accent)); card.setStrokeWidth(selected ? ui.dp(1) : 0);
        card.setSelected(selected); card.setClickable(true); card.setEnabled(!model.busy);
        card.setOnClickListener(view -> click.run());
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        // 后端不返回图片：优先用 App 自带照片，其他城市查维基百科配图。
        if (!photoCity.isEmpty()) {
            com.google.android.material.imageview.ShapeableImageView image = new com.google.android.material.imageview.ShapeableImageView(this);
            image.setShapeAppearanceModel(com.google.android.material.shape.ShapeAppearanceModel.builder().setAllCornerSizes(ui.dp(12)).build());
            TripPhotos.show(image, photoCity);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, ui.dp(72)); params.bottomMargin = ui.dp(8);
            body.addView(image, params);
        }
        TextView name = new TextView(this); name.setText(title); name.setTextSize(16);
        AppFonts.style(name, android.graphics.Typeface.BOLD);
        name.setTextColor(ui.color(selected ? R.color.on_accent : R.color.text_primary));
        // 地名选项只有一行，文字居中。
        if (detail.isEmpty() && model.selected <= 1) name.setGravity(android.view.Gravity.CENTER);
        body.addView(name);
        if (!detail.isEmpty()) {
            TextView caption = new TextView(this); caption.setText(detail); caption.setTextSize(13);
            AppFonts.style(caption, android.graphics.Typeface.NORMAL);
            caption.setTextColor(ui.color(selected ? R.color.on_accent : R.color.text_secondary));
            caption.setPadding(0, ui.dp(2), 0, 0);
            body.addView(caption);
        }
        card.addView(body);
        return card;
    }
    private void renderCalendar() {
        ui.content.addView(new TripCalendarView(this, model.dates, month, date -> { model.dates.select(date); render(); }, this::render));
        if (model.dates.isComplete()) {
            int duration = TripDates.daysBetweenInclusive(model.dates.getStartDate(), model.dates.getEndDate());
            if (duration > 60) ui.text("Choose a trip of at most 60 days.", 14, false);
            else button("Confirm dates", R.color.lime, () -> send("Fixed dates: depart " + LivePlanningProtocol.isoDate(model.dates.getStartDate())
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
        // 旧计划可能已确认了超过上限的人数，先改人数才能生成。
        if (LivePlanningProtocol.tooManyAdults(steps.getJSONObject("COMPANIONS").optJSONObject("answer"))) {
            ui.card("Too many travellers", LivePlanningProtocol.ADULT_LIMIT + " Change who’s coming along to continue.", R.color.peach, null);
            button("Change travellers", R.color.lime, () -> model.select(3), "live-change-travellers");
            LinearLayout row = new LinearLayout(this);
            ui.content.addView(row, new LinearLayout.LayoutParams(-1, -2));
            rowButton(row, "Back to home", R.color.surface, this::abandon, "live-abandon-plan", 0);
            return;
        }
        // 失败或结果不确定时，同一个位置变成 Generate again，不会同时出现两个生成按钮。
        boolean again = model.generationUncertain || !model.generationFailure.isEmpty();
        if (!model.generationFailure.isEmpty()) ui.card("We couldn’t build this trip", model.generationFailure, R.color.peach, null);
        else if (model.generationUncertain) ui.card("We lost track of your trip", "It may still be generating or already saved. Check your saved trips before trying again.", R.color.peach, null);
        if (again) {
            button("Generate again", R.color.lime, this::generateAgain, "live-generate");
            LinearLayout row = new LinearLayout(this);
            ui.content.addView(row, new LinearLayout.LayoutParams(-1, -2));
            rowButton(row, "Check saved trips", R.color.mint, model::listTrips, "live-recover", 0);
            rowButton(row, "Back to home", R.color.surface, this::abandon, "live-abandon-plan", ui.dp(10));
        } else button("Generate my trip", R.color.lime, model::generate, "live-generate");
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
        ui.text("Usually takes 2–4 minutes", 14, false);
        ui.text(model.status, 19, true);
        ui.text("You can return to home. We’ll keep building it while Odyssey stays open.", 14, false);
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
        rowButton(row, "View My Trips", R.color.surface, () -> openHomeTab(R.id.nav_my), "live-my-trips", 0);
        rowButton(row, "Plan another trip", R.color.surface, model::newSession, "live-fresh", ui.dp(10));
    }
    /** 回复或生成结果不确定时的找回操作；不会自动重发，避免重复提交和重复消耗额度。 */
    private void renderRecovery() {
        if (model.pendingTurn != null && !model.busy) {
            ui.card("Your last reply didn’t come back", "Check it first so nothing is sent twice.", R.color.peach, null);
            button("Check the reply", R.color.lime, model::replay, "live-replay");
            button("Go back to my answers", R.color.surface, model::abandonTurn, "live-abandon");
        }
        if (model.generationUncertain && !model.busy && model.session == null) {
            ui.card("We lost track of your trip", "It may still be generating or already saved. Check your saved trips.", R.color.peach, null);
            button("Check saved trips", R.color.mint, model::listTrips, "live-recover");
            button("Back to home", R.color.surface, this::abandon, "live-abandon-plan");
        }
        if (!model.resultJson.isEmpty() && model.generationUncertain && !model.busy) button("Save the itinerary again", R.color.lime, model::retrySave, "live-retry-save");
        for (int i = 0; i < model.serverTrips.length(); i++) {
            JSONObject trip = model.serverTrips.optJSONObject(i);
            if (trip == null) continue;
            String id = trip.optString("id");
            ui.card(trip.optString("title", "Saved trip"), trip.optString("created_at"), R.color.surface, model.busy ? null : () -> model.recover(id));
        }
    }
    private void generateAgain() {
        if (!model.generationUncertain) { model.generateAgain(); return; }
        // 结果不确定时上一个请求可能还会完成，再生成可能多出一份。
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Generate again?").setMessage("The earlier request may still finish, so you could end up with two copies of this trip.")
                .setNegativeButton("Keep checking", null).setPositiveButton("Generate again", (d, w) -> model.generateAgain()).show();
    }
    private void abandon() { model.discardPlan(); openHomeTab(R.id.nav_home); }
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
    /** 助手在左带头像，用户在右。 */
    private void bubble(String value, boolean user) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(user ? android.view.Gravity.END : android.view.Gravity.START);
        if (!user) {
            android.widget.ImageView avatar = new android.widget.ImageView(this);
            avatar.setImageResource(R.drawable.ic_robot);
            avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            avatar.setPadding(ui.dp(7), ui.dp(7), ui.dp(7), ui.dp(7));
            android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
            circle.setShape(android.graphics.drawable.GradientDrawable.OVAL); circle.setColor(ui.color(R.color.purple));
            avatar.setBackground(circle);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)); params.setMarginEnd(ui.dp(8));
            row.addView(avatar, params);
        }
        TextView bubble = new TextView(this); bubble.setText(value); bubble.setTextSize(14);
        AppFonts.style(bubble, android.graphics.Typeface.NORMAL);
        bubble.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10));
        bubble.setTextColor(ui.color(user ? R.color.on_accent : R.color.text_primary));
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(ui.color(user ? R.color.purple_light : R.color.surface));
        int big = ui.dp(18), small = ui.dp(4);
        // 靠近说话人一侧的上角收小，形成对话框的感觉。
        shape.setCornerRadii(user ? new float[]{big, big, small, small, big, big, big, big} : new float[]{small, small, big, big, big, big, big, big});
        bubble.setBackground(shape);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-2, -2);
        if (user) textParams.setMarginStart(ui.dp(44));
        // 权重让长文字换行，而不是把头像挤出屏幕。
        else { textParams = new LinearLayout.LayoutParams(0, -2, 1f); }
        row.addView(bubble, textParams);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2); rowParams.setMargins(0, ui.dp(5), 0, ui.dp(5));
        ui.content.addView(row, rowParams);
    }
    private MaterialButton button(String text, int background, Runnable click, String tag) {
        MaterialButton button = ui.button(text, click);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ui.color(background)));
        if (background == R.color.surface) button.setTextColor(ui.color(R.color.text_primary));
        button.setTag(tag); button.setEnabled(!model.busy || tag.equals("live-home"));
        return button;
    }
    private void rowButton(LinearLayout row, String text, int background, Runnable click, String tag, int margin) {
        MaterialButton button = button(text, background, click, tag);
        ui.content.removeView(button);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f); params.setMarginStart(margin);
        row.addView(button, params);
    }
    private void text(int id, String value) { ((TextView) findViewById(id)).setText(value); }
}
