package com.example.odyssey;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.os.BundleCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 七轮规划的协调者。PlanningSession 保存固定槽位，Renderer 只画控件；
 * 所有确认/文字提交均先序列化为请求，再由本机 Mock 回包，统一校验后才更新页面。
 * 出发地是 START 前置步骤，不占用七段进度；正式生成仍使用经过验证的旧行程适配器。
 */
public class MainActivity extends AppCompatActivity implements PlannerFormRenderer.Actions, PlanningSessionRenderer.Actions, GenerationDemoRenderer.Actions {
    private PlanningSession session;
    private TripPlanState state = new TripPlanState();
    private Calendar calendarMonth = Calendar.getInstance();
    private PlanningSessionRenderer form;
    private PlannerFormRenderer generationForm;
    private AppCompatEditText input;
    private TextView feedback;
    private NestedScrollView scroll;
    private OnBackPressedCallback backCallback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MockPlanningService service = new MockPlanningService();
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor();
    private String generationId;
    private int saveStatus;
    private boolean loading;
    // 每次导航/生命周期中止都会增加票号。回调持有旧票号时不能改变当前界面。
    private int operation;
    private Runnable pending;
    private String error = "";
    private JSONObject retryInput;
    private String retryAction;
    private long demoStartedAt;
    private int demoStage, demoRenderedStage = -1;
    private boolean demoOffline, demoSummary;
    // Tick 只读取本机单调时钟；不会伪装成后端事件或在每次重绘时重新计时。
    private final Runnable demoTick = this::updateDemo;
    private void updateDemo() {
        if (!state.isGenerating()) return;
        long elapsed = Math.max(0, android.os.SystemClock.elapsedRealtime() - demoStartedAt);
        if (!demoOffline) {
            int nextStage = GenerationDemo.stage(elapsed);
            if (elapsed >= GenerationDemo.DURATION_MS) { finishDemo(); return; }
            if (nextStage != demoRenderedStage) { demoStage = nextStage; demoRenderedStage = nextStage; render(false); }
        }
        handler.removeCallbacks(demoTick);
        handler.postDelayed(demoTick, 1000);
    }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        // Bundle 只负责旋转/系统重建时的临时恢复；正式行程仍由 TripStore 保存在本机。
        // session 是八槽位问答；state 仅在生成阶段充当旧行程模型的适配器。
        if (saved != null) {
            session = BundleCompat.getSerializable(saved, "planning_session", PlanningSession.class);
            TripPlanState restored = BundleCompat.getSerializable(saved, "trip_state", TripPlanState.class);
            if (restored != null) state = restored;
            calendarMonth = TripDates.calendar(saved.getInt("calendar_month", TripDates.key(calendarMonth)));
            generationId = saved.getString("generation_id"); saveStatus = saved.getInt("trip_save_status", 0);
            error = saved.getString("planning_error", "");
            demoStartedAt = saved.getLong("demo_started_at", 0);
            demoStage = saved.getInt("demo_stage", 0);
            demoOffline = saved.getBoolean("demo_offline"); demoSummary = saved.getBoolean("demo_summary");
        }
        if (session == null) {
            String residence = "", preferences = "", profileRequest = "";
            try {
                JSONObject profile = new ProfileStore(this).load();
                if (profile != null) {
                    // 加载已完成资料，构造将来创建会话的 JSON；目前只交给本机 Mock。
                    if (ProfileStore.isComplete(profile)) profileRequest = ProfilePayload.createSession(profile).toString();
                    residence = InputValidation.optionalText(profile.opt("residential_location"), 200, "Residence");
                    preferences = InputValidation.optionalText(profile.opt("travel_preferences"), 2000, "Preferences");
                    org.json.JSONArray tags = profile.optJSONArray("preference_tags");
                    if (tags != null) for (int i = 0; i < tags.length(); i++) {
                        String label = PreferenceTags.ALL.get(tags.getString(i));
                        if (label != null) {
                            String combined = preferences + (preferences.isEmpty() ? "" : " · ") + label;
                            if (combined.length() <= 2000) preferences = combined;
                        }
                    }
                }
            } catch (Exception ignored) { /* 读档失败时只使用可见的本机默认建议，不伪造用户资料。 */ }
            session = new PlanningSession(residence, InputValidation.optionalText(preferences, 2000, "Preferences"));
            session.profileRequest = profileRequest;
        }
        // 旧 Bundle 在增加字段后可能反序列化为 null；保持旧草稿仍可恢复。
        if (session.profileRequest == null) session.profileRequest = "";
        calendarMonth.set(Calendar.DAY_OF_MONTH, 1);
        EdgeToEdge.enable(this); setContentView(R.layout.activity_main);
        scroll = findViewById(R.id.main); input = findViewById(R.id.messageInput); feedback = findViewById(R.id.inputFeedback);
        form = new PlanningSessionRenderer(findViewById(R.id.stepContent), this);
        generationForm = new PlannerFormRenderer(findViewById(R.id.stepContent), state, calendarMonth, this);
        // 三个提交入口最终共用 dispatch，避免键盘发送与按钮采用不同的确认规则。
        findViewById(R.id.backButton).setOnClickListener(view -> back());
        findViewById(R.id.nextButton).setOnClickListener(view -> next());
        findViewById(R.id.sendButton).setOnClickListener(view -> submitMessage());
        input.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEND) return false;
            submitMessage(); return true;
        });
        backCallback = new OnBackPressedCallback(false) { @Override public void handleOnBackPressed() { back(); } };
        getOnBackPressedDispatcher().addCallback(this, backCallback);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        render(false);
        if (state.isGenerated() && saveStatus != 1) saveTrip();
    }
    /** 每次重画都读取答案/草稿，控件实例本身不承担业务状态。 */
    private void render(boolean top) {
        boolean generation = state.isGenerating() || state.isGenerated();
        text(R.id.planHeadline, generation ? getString(state.isGenerating() ? R.string.generation_loading_title : R.string.generation_done_title) : session.step().question.title);
        text(R.id.assistantTitle, generation ? getString(state.isGenerating() ? R.string.generation_loading_prompt : R.string.generation_done_prompt) : "Let’s shape your trip");
        text(R.id.assistantMessage, generation ? getString(state.isGenerating() ? R.string.generation_loading_help : R.string.generation_done_help) : session.step().question.description);
        text(R.id.stepText, generation ? "DEMO" : session.progress());
        int[] ids = {R.id.progressStep1, R.id.progressStep2, R.id.progressStep3, R.id.progressStep4, R.id.progressStep5, R.id.progressStep6, R.id.progressStep7};
        // current=0 是 START，没有亮段；current=1 对应第一轮目的地，亮第一段。
        for (int i = 0; i < ids.length; i++) findViewById(ids[i]).setBackgroundColor(ContextCompat.getColor(this,
                generation || i < session.current ? R.color.lime : R.color.outline));
        findViewById(R.id.tripBoard).setVisibility(state.isGenerating() || session.answer(0).isEmpty() ? View.GONE : View.VISIBLE);
        android.widget.ImageView photo = findViewById(R.id.tripBoardPhoto);
        photo.setVisibility(session.answer(1).isEmpty() ? View.GONE : View.VISIBLE);
        if (!session.answer(1).isEmpty()) {
            // Live Trip 使用已确认选项的响应 URL，不把用户输入的城市拼成图库查询。
            String imageUrl = null;
            for (PlanningSession.Option option : session.steps[1].question.options)
                if (option.value.equals(session.answer(1))) imageUrl = option.imageUrl;
            String credit = imageUrl != null && imageUrl.equals(MockPhotoCatalog.url(session.answer(1), true)) ? "Photo: Unsplash · illustrative" : "";
            TripPhotos.showResponse(photo, new ResponseImage(imageUrl, session.answer(1) + " atmosphere photo", credit),
                    session.answer(1), findViewById(R.id.tripBoardPhotoCredit));
        } else SafeImageLoader.SHARED.cancel(photo);
        text(R.id.tripBoardDestination, session.answer(1).isEmpty() ? "Depart from " + session.answer(0) : session.answer(0) + " → " + session.answer(1));
        text(R.id.tripBoardStatus, "Local demo · " + (session.answer(2).isEmpty() ? "Dates to choose" : session.answer(2)));
        View next = findViewById(R.id.nextButton);
        next.setVisibility(generation ? View.INVISIBLE : View.VISIBLE);
        // NEEDS_REVIEW 不允许用浏览箭头静默确认；用户必须点击当前选项或明确确认按钮。
        next.setEnabled(!loading && !session.step().state.equals("NEEDS_REVIEW") && (session.step().state.equals("CONFIRMED") || !session.step().selected.isEmpty()
                || session.current == 2 && session.dateDraft.isStepComplete(2) || !session.step().draft.isEmpty() || !session.step().chatCandidate.isEmpty()));
        next.setContentDescription(session.current == 7 ? "Generate demo trip" : "Next step");
        findViewById(R.id.backButton).setEnabled(true);
        backCallback.setEnabled(session.current > 0 || generation);
        if (state.isGenerating()) {
            new GenerationDemoRenderer(findViewById(R.id.stepContent), state, this).render(demoStage, demoOffline, demoSummary);
        } else if (generation) generationForm.render(); else form.render(session, calendarMonth, loading);
        findViewById(R.id.composerContainer).setVisibility(generation ? View.GONE : View.VISIBLE);
        input.setEnabled(!loading); findViewById(R.id.sendButton).setEnabled(!loading);
        input.setHint(session.current < 3 ? "Describe a change to refresh suggestions…" : "Describe your answer…");
        input.setText(session.step().draft); input.setSelection(input.length());
        feedback.setText(error.isEmpty() ? session.feedback : error);
        feedback.setVisibility(feedback.length() == 0 ? View.GONE : View.VISIBLE);
        feedback.setOnClickListener(error.isEmpty() ? null : view -> retry());
        if (top) scroll.post(() -> scroll.smoothScrollTo(0, 0));
        if (state.isGenerating()) {
            if (demoStartedAt == 0 || demoStartedAt > android.os.SystemClock.elapsedRealtime()) demoStartedAt = android.os.SystemClock.elapsedRealtime();
            handler.removeCallbacks(demoTick); handler.postDelayed(demoTick, 1000);
        }
    }
    @Override public void option(String id) {
        if (loading) return;
        saveDraft(); session.toggle(id); error = "";
        if (session.current == 2) session.dateDraft.useFlexibleDates();
        // 城市卡片只选择；用户点明确的确认按钮（或前进箭头）才提交，允许先比较建议。
        // 同行人/限制为多选，只有 Continue 才确认；预算与追问仍可点击接受。
        if (session.current < 2 || session.current == 3 || session.current == 5) render(false); else confirm();
    }
    @Override public void confirm() {
        if (loading) return;
        saveDraft();
        try {
            // 最后一轮也接受文字；从生成页返回后再生成时，明确重交已确认文字而非空选项。
            if (session.current == 7 && session.step().state.equals("CONFIRMED") && session.step().selected.isEmpty()) {
                dispatch("SUBMIT", PlanningJson.textInput(session.step().answer)); return;
            }
            // 日历只维护候选区间；到此点击确认才生成 DATE_RANGE 请求并提交给 Mock。
            JSONObject answer = session.current == 2 && !session.dateDraft.isFlexibleDates()
                    && session.dateDraft.isStepComplete(2) ? PlanningJson.dateInput(session.dateDraft)
                    : PlanningJson.optionsInput(session.step().selected);
            dispatch("CONFIRM", answer);
        } catch (Exception exception) { fail(exception); }
    }
    private void submitMessage() {
        if (loading) return;
        saveDraft();
        // 城市/日期文字用于刷新建议，保持原槽位；第 3–7 轮文字是可接受的正式答案。
        try { dispatch(session.current < 3 ? "REFINE" : "CHAT", PlanningJson.textInput(input.getText() == null ? "" : input.getText().toString())); }
        catch (Exception exception) { fail(exception); }
    }
    /** 捕获不可变 JSON 快照与票号；模拟异步延迟不会共享可变 session 引用。 */
    private void dispatch(String action, JSONObject answer) {
        try {
            JSONObject request = PlanningJson.request(session, action, answer);
            String snapshot = PlanningJson.snapshot(session).toString();
            int ticket = ++operation;
            retryInput = answer; retryAction = action; loading = true; error = ""; hideKeyboard(); render(false);
            pending = () -> {
                if (ticket != operation || isDestroyed()) return;
                try {
                    String response = service.respond(request.toString(), snapshot);
                    if (ticket != operation) return;
                    PlanningJson.Applied applied = PlanningJson.applyResult(session, request, response);
                    session = applied.session;
                    loading = false; pending = null; retryInput = null; retryAction = null;
                    if (applied.navigation.equals("GENERATE")) {
                        // 全八槽位均确认后才能转换旧模型；生成 ID 只在新一轮生成时创建。
                        // 后续保存失败重试/旋转恢复继续复用此 ID，避免行程库新增重复记录。
                        state = session.toPlan(); generationForm = new PlannerFormRenderer(findViewById(R.id.stepContent), state, calendarMonth, this);
                        generationId = UUID.randomUUID().toString(); saveStatus = 0;
                        demoStartedAt = android.os.SystemClock.elapsedRealtime(); demoStage = 0; demoRenderedStage = -1;
                        demoOffline = false; demoSummary = false;
                    }
                    render(!action.equals("REFINE") && !action.equals("CHAT"));
                    if (action.equals("CHAT") || action.equals("REFINE")) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
                } catch (Exception exception) { loading = false; pending = null; fail(exception); }
            };
            handler.postDelayed(pending, service.delayMillis);
        } catch (Exception exception) { loading = false; fail(exception); }
    }
    @Override public void useChatAnswer() {
        if (loading || session.step().chatCandidate.isEmpty()) return;
        try { dispatch("SUBMIT", PlanningJson.textInput(session.step().chatCandidate)); }
        catch (Exception exception) { fail(exception); }
    }
    private void retry() {
        if (retryInput != null) dispatch(retryAction, retryInput); else if (!session.step().draft.isEmpty()) submitMessage(); else confirm();
    }
    private void fail(Exception exception) { error = exception.getMessage() + " · Tap here to retry"; render(false); }
    private void next() {
        if (loading) return;
        saveDraft();
        if (!session.step().draft.isEmpty()) { submitMessage(); return; }
        if (session.current >= 3 && !session.step().chatCandidate.isEmpty() && session.step().selected.isEmpty()) { useChatAnswer(); return; }
        if (session.step().state.equals("CONFIRMED") && session.current < 7) { cancelOperation(); session.browse(1); render(true); }
        else confirm();
    }
    @Override public void back() {
        saveDraft(); cancelOperation(); hideKeyboard();
        if (state.isGenerating() || state.isGenerated()) { handler.removeCallbacks(demoTick); demoStartedAt = 0; state.goBack(); session.current = 7; }
        else if (session.current > 0) session.browse(-1);
        else { finish(); return; }
        error = "";
        render(true);
    }
    private void cancelOperation() {
        // 不持久化 loading：取消后保留 draft/selected/dateDraft，恢复页面即可明确重试。
        ++operation;
        if (pending != null) handler.removeCallbacks(pending);
        if (loading) error = "Update interrupted. Your draft is retained · Tap here to retry";
        loading = false; pending = null; retryInput = null; retryAction = null;
    }
    @Override public void date(int date) {
        if (session.current != 2 || state.isGenerating() || state.isGenerated()) return;
        // 日历仍可在 loading 时修改；先取消旧确认并更新票号，迟到回包不能覆盖新区间。
        cancelOperation();
        saveDraft(); boolean earlier = session.dateDraft.getStartDate() != 0 && session.dateDraft.getEndDate() == 0 && date < session.dateDraft.getStartDate();
        session.selectDate(date); session.steps[2].selected.clear(); session.steps[2].state = "AWAITING_INPUT";
        error = earlier ? "Departure changed. Now choose your return date." : ""; render(false);
    }
    @Override public void redraw() { saveDraft(); render(false); }
    @Override public void changed() { redraw(); }
    @Override public void dateSelected(int date) { date(date); }
    @Override public void chooseDeparture(String city) { /* 旧 Renderer 只用于生成页，不再创建城市卡。 */ }
    @Override public void editLocation(boolean departure) { session.current = departure ? 0 : 1; render(true); }
    private void saveDraft() {
        if (input != null && !state.isGenerating() && !state.isGenerated()) {
            try { session.step().draft = InputValidation.optionalText(input.getText() == null ? "" : input.getText().toString(), 500, "Message"); }
            catch (IllegalArgumentException exception) { error = exception.getMessage(); }
        }
    }
    private void hideKeyboard() { input.clearFocus(); new WindowInsetsControllerCompat(getWindow(), input).hide(WindowInsetsCompat.Type.ime()); }
    private void text(int id, String text) { ((TextView) findViewById(id)).setText(text); }
    @Override public void toggleSummary() { demoSummary = !demoSummary; render(false); }
    @Override public void toggleConnection() { demoOffline = !demoOffline; render(false); if (!demoOffline) updateDemo(); }
    @Override public void finishDemo() {
        if (!state.isGenerating()) return;
        handler.removeCallbacks(demoTick); demoOffline = false;
        state.finishGeneration(); render(true); saveTrip();
    }
    @Override public void returnHome() {
        // 在首页上提供返回入口，原规划 Activity 保留在返回栈中；这不是持久后台任务。
        startActivity(new Intent(this, HomeActivity.class).putExtra(HomeActivity.EXTRA_RETURN_TO_DEMO, true));
    }
    @Override public int saveStatus() { return saveStatus; }
    @Override public void saveTrip() {
        if (!state.isGenerated()) return;
        if (generationId == null) generationId = UUID.randomUUID().toString();
        String savingId = generationId;
        SavedTrip snapshot;
        // 在主线程取得独立快照，随后用户编辑答案不会改变已经排队的保存内容。
        try { snapshot = SavedTrip.fromPlan(this, state, savingId); }
        catch (IllegalArgumentException exception) { saveStatus = 2; render(false); return; }
        TripStore store = new TripStore(getApplicationContext()); saveStatus = 0; render(false);
        saveExecutor.execute(() -> {
            boolean saved = store.save(snapshot);
            runOnUiThread(() -> {
                // 磁盘任务可能晚于退出或另一轮生成；旧保存回调只能结束自己的那一轮。
                if (isDestroyed() || !savingId.equals(generationId) || !state.isGenerated()) return;
                saveStatus = saved ? 1 : 2; render(false);
            });
        });
    }
    @Override public void viewSavedTrips() {
        startActivity(new Intent(this, HomeActivity.class).putExtra(HomeActivity.EXTRA_OPEN_MY, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)); finish();
    }
    @Override public void viewItinerary() {
        if (saveStatus == 1 && generationId != null) startActivity(new Intent(this, TripDetailActivity.class).putExtra(TripDetailActivity.EXTRA_ID, generationId));
    }
    @Override protected void onStart() {
        super.onStart();
        // onStop 已取消回包并保留草稿；再次可见时必须重画，恢复被 loading 禁用的输入。
        if (input != null) render(false);
    }
    @Override protected void onStop() { saveDraft(); cancelOperation(); handler.removeCallbacks(demoTick); super.onStop(); }
    @Override protected void onDestroy() { cancelOperation(); handler.removeCallbacks(demoTick); saveExecutor.shutdown(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        saveDraft(); cancelOperation();
        out.putSerializable("planning_session", session); out.putSerializable("trip_state", state);
        out.putLong("demo_started_at", demoStartedAt); out.putInt("demo_stage", demoStage);
        out.putBoolean("demo_offline", demoOffline); out.putBoolean("demo_summary", demoSummary);
        out.putString("generation_id", generationId); out.putInt("trip_save_status", saveStatus);
        out.putInt("calendar_month", TripDates.key(calendarMonth)); out.putString("planning_error", error);
        super.onSaveInstanceState(out);
    }
}
