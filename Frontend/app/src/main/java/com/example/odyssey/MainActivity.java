// 当前类所属的包（命名空间）；同包的 TripPlanState 等类可以直接用类名访问。
package com.example.odyssey;

// import 让下面可以写 Bundle 等短类名，不必每次写 android.os.Bundle；它不会创建对象。
// Bundle 装页面临时状态，Intent 描述要跳转的页面和随身携带的参数。
import android.os.Bundle;
import android.content.Intent;
// Handler 把任务交给指定线程的消息队列；Looper.getMainLooper() 对应 UI 主线程。
import android.os.Handler;
import android.os.Looper;
// View 是控件基类；EditorInfo 定义软键盘“发送”等动作；TextView 用于显示文字。
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
// AndroidX 提供兼容不同 Android 版本的页面、返回事件、窗口边距和控件工具。
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
// MaterialButton 是 Material 风格按钮；Calendar 表示当前浏览的月份，UUID 生成行程 ID。
import com.google.android.material.button.MaterialButton;
import java.util.Calendar;
import java.util.UUID;
// ExecutorService 是后台任务队列；Executors 用于创建它，这里仅拿它保存本机行程。
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 五步规划页的协调者：接收点击/输入，更新 TripPlanState，再让 Renderer 重画界面。
 * XML 提供固定区域，PlannerFormRenderer 负责每一步的动态选项；本类负责导航和生命周期。
 * 阅读主流程：onCreate → render → 用户操作 → state 更新 → render → saveTrip。
 * 当前生成过程完全是本地演示，没有调用后端模型。
 *
 * 阅读提示：private=仅本类使用；void=不返回结果；boolean=只有 true/false 两种值。
 * 条件 ? A : B 表示“条件为真用 A，否则用 B”；! 取反，&& 两边都真，|| 任一为真。
 * view -> back() 是回调：现在登记，等用户点击时才执行 back()，不是登记时立刻执行。
 * R.id.xxx 是控件编号，R.string.xxx 是文案编号，R.layout.xxx 是布局编号，都由资源生成。
 * 本类名字虽叫 MainActivity，但现在负责五步规划；应用的三个底部栏目在 HomeActivity。
 */
// extends 继承 Android 页面的能力；implements 承诺实现表单 Actions 接口中的回调方法。
public class MainActivity extends AppCompatActivity implements PlannerFormRenderer.Actions {
    // new 创建一个对象；state 保存五步答案与进度，不是屏幕上的控件。
    private TripPlanState state = new TripPlanState();
    // 初始使用当前日期所在月；后面会将日设为 1，专门用来控制日历翻月。
    private Calendar calendarMonth = Calendar.getInstance();
    // true：第一步输入框正在填出发地；false：正在填目的地；两者都属于第 1 步。
    private boolean editingDeparture = true;
    // 声明动态表单渲染器；稍后 onCreate 找到 XML 容器后才创建它。
    private PlannerFormRenderer form;
    // 输入控件的引用；input 不是输入的字符串，真正文字要通过 getText() 获取。
    private AppCompatEditText input;
    // 输入框附近的提示文字，例如“备注已保存”或日期重新选择提示。
    private TextView feedback;
    // 整页滚动容器，用于切换步骤后回顶部、编辑时滚到输入框。
    private NestedScrollView scroll;
    // 接管系统返回手势/返回键的回调，和页面左上角的返回按钮共享 back()。
    private OnBackPressedCallback backCallback;
    // 同一次生成重试保存时复用 ID，避免“我的行程”出现重复记录。
    private String generationId;
    private int saveStatus; // int 字段默认 0；0=等待/正在保存，1=保存成功，2=保存失败。
    // 磁盘写入放在单线程队列；界面更新仍回到主线程执行。
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor();
    // final 表示不再换成另一个 Handler 对象；并不表示 Handler 本身不能接收新任务。
    private final Handler handler = new Handler(Looper.getMainLooper());
    // Runnable 是“一段稍后执行的代码”；这里只定义任务，scheduleMock() 才安排执行时间。
    private final Runnable finishMock = () -> {
        // 用户可能已经点返回，因此执行时重新检查，只有仍在生成才继续。
        if (state.isGenerating()) {
            // 将演示状态从 1（生成中）改成 2（完成），此时尚未成功写入磁盘。
            state.finishGeneration();
            // true 表示重画完成页后滚回顶部。
            render(true);
            // 开始真正的本机保存；与上面的“演示生成完成”不是同一件事。
            saveTrip();
        }
    };

    // Android 创建此页面时调用；@Override 表示重写父类方法，protected 是访问范围。
    @Override protected void onCreate(Bundle savedInstanceState) {
        // 先让父类完成页面基础初始化，super 指父类 AppCompatActivity 的实现。
        super.onCreate(savedInstanceState);
        // Bundle 是页面重建时的临时状态，不是长期存储；正式行程由 TripStore 保存。
        if (savedInstanceState != null) {
            // 页面重建时按键名取回表单对象；TripPlanState.class 用于核对期望的对象类型。
            TripPlanState restored = BundleCompat.getSerializable(savedInstanceState,
                    "trip_state", TripPlanState.class);
            // 取到对象才替换默认空表单；没有该键时继续使用上面 new 出来的初始 state。
            if (restored != null) state = restored;
            // 复用本轮生成的 ID；如果需要重试保存，就覆盖同一个键而不是新增重复行程。
            generationId = savedInstanceState.getString("generation_id");
            // getInt 的第二个参数 0 是找不到该键时使用的默认值。
            saveStatus = savedInstanceState.getInt("trip_save_status", 0);
            // 优先恢复正在编辑哪个位置；旧状态未存此键时，用“出发地是否为空”推断。
            editingDeparture = savedInstanceState.getBoolean("editing_departure", state.getDeparture().isEmpty());
            // 日期以 20261101 这样的整数存储；没有保存值时用当前 calendarMonth 的日期。
            int month = savedInstanceState.getInt("calendar_month", TripDates.key(calendarMonth));
            // 将整数日期转回 Calendar 对象，交给日历组件使用。
            calendarMonth = TripDates.calendar(month);
        }
        // 把日历固定在每月 1 日，避免从 31 日翻到没有 31 日的月份时出现意外跳月。
        calendarMonth.set(Calendar.DAY_OF_MONTH, 1);
        // 允许内容延伸到系统栏区域；后面监听 insets，再为内容补上安全边距。
        EdgeToEdge.enable(this);
        // 加载 activity_main.xml；必须先加载，才能通过 findViewById 找到其中的控件。
        setContentView(R.layout.activity_main);
        // 下面三个赋值只获得已有控件的引用，不会新建滚动区或输入框。
        scroll = findViewById(R.id.main);
        // 对应 XML 中 android:id="@+id/messageInput" 的输入框。
        input = findViewById(R.id.messageInput);
        // 对应输入框附近专门显示反馈的 TextView。
        feedback = findViewById(R.id.inputFeedback);
        // 四个参数：动态容器、共享答案对象、浏览月份、操作回调接收者。
        // this 就是当前 MainActivity；因为实现了 Actions，它能接收 changed/dateSelected 等通知。
        form = new PlannerFormRenderer(findViewById(R.id.stepContent), state, calendarMonth, this);
        // 点击左箭头时调用 back；view 是被点击的按钮，本回调无需使用它。
        findViewById(R.id.backButton).setOnClickListener(view -> back());
        // 点击右箭头时调用 next；第五步的 next 会进入生成，而不是第六步。
        findViewById(R.id.nextButton).setOnClickListener(view -> next());
        // 输入框旁的发送按钮与软键盘发送共用 submitMessage，避免两套提交规则。
        findViewById(R.id.sendButton).setOnClickListener(view -> submitMessage());
        // view 是输入控件，actionId 是键盘动作编号，event 是可能附带的按键事件。
        input.setOnEditorActionListener((view, actionId, event) -> {
            // 不是“发送”就不处理，返回 false 让控件继续按默认方式处理这个动作。
            if (actionId != EditorInfo.IME_ACTION_SEND) return false;
            // 键盘发送与屏幕发送按钮走同一个方法。
            submitMessage();
            // true 告诉 Android：这个键盘动作已被消费，不必再做默认处理。
            return true;
        });
        // 匿名子类：直接在这里写返回回调实现；false 表示先禁用，render 会按步骤启用。
        backCallback = new OnBackPressedCallback(false) {
            // 当回调处于启用状态时，系统返回操作也进入本页 back()。
            @Override public void handleOnBackPressed() { back(); }
        };
        // 与当前 Activity 的生命周期绑定，让系统知道何时应使用这个返回回调。
        getOnBackPressedDispatcher().addCallback(this, backCallback);
        // 同时避让系统栏和软键盘，防止底部输入框被遮住。
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            // | 是位标记组合：同时请求系统栏和输入法占用空间；ime 就是软键盘。
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            // 将四边占用的像素数作为内容内边距，避免文字/输入框落在遮挡区域。
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            // 返回原 insets，表示不吞掉它，后续 View 仍可接收到窗口边距信息。
            return insets;
        });
        // 第一次根据当前 state 填充页面；false 表示不主动滚回顶部。
        render(false);
        // 重建时若已生成但尚未确认保存成功，就用同一生成 ID 再尝试保存。
        if (state.isGenerated() && saveStatus != 1) saveTrip();
    }

    /** 根据当前状态统一刷新标题、进度、Live Trip 和表单；不在 View 中保存业务答案。 */
    private void render(boolean scrollToTop) {
        // 两种状态都属于生成区域：正在生成，或者已生成完成。
        boolean generation = state.isGenerated() || state.isGenerating();
        // 返回 1～5 的业务步骤号；访问 Java 数组时要减 1。
        int step = state.getCurrentStep();
        // 从资源数组读取五个大标题；例如第一步用 titles[0]，不是 titles[1]。
        String[] titles = getResources().getStringArray(R.array.planner_headlines);
        // 读取每一步 AI 提示的标题，界面语言仍由英文资源决定。
        String[] prompts = getResources().getStringArray(R.array.planner_prompts);
        // 读取每一步 AI 提示下面的说明文字。
        String[] help = getResources().getStringArray(R.array.planner_help);
        // 外层问“是否在生成区域”；是则内层区分生成中/完成，否则按步骤取普通标题。
        text(R.id.planHeadline, generation ? getString(state.isGenerating()
                ? R.string.generation_loading_title : R.string.generation_done_title) : titles[step - 1]);
        // 普通表单中再特判：第 1 步且已切到目的地编辑时，显示“选择目的地”提示。
        text(R.id.assistantTitle, generation ? getString(state.isGenerating()
                ? R.string.generation_loading_prompt : R.string.generation_done_prompt)
                : step == 1 && !editingDeparture ? getString(R.string.choose_destination_prompt) : prompts[step - 1]);
        // 说明文字也在生成中、生成完成、普通步骤这三种内容之间选择。
        text(R.id.assistantMessage, generation ? getString(state.isGenerating()
                ? R.string.generation_loading_help : R.string.generation_done_help) : help[step - 1]);
        // 生成区域显示演示标签；其他时候将 step 和 TOTAL_STEPS 填入资源的格式占位符。
        text(R.id.stepText, generation ? getString(R.string.demo_badge)
                : getString(R.string.step_count, step, TripPlanState.TOTAL_STEPS));
        // 按视觉顺序保存五段进度条的控件 ID，方便用一个循环统一设置颜色。
        int[] progressIds = {R.id.progressStep1, R.id.progressStep2, R.id.progressStep3,
                R.id.progressStep4, R.id.progressStep5};
        for (int i = 0; i < progressIds.length; i++) {
            // i 从 0 开始：step=2 时 i=0/1 亮绿，后面三段灰色，当前步骤也算亮起。
            // getColor 将颜色资源编号转为实际色值，自动使用当前浅色/深色资源。
            findViewById(progressIds[i]).setBackgroundColor(ContextCompat.getColor(this,
                    i < step ? R.color.lime : R.color.outline));
        }
        // GONE 不显示也不占空间；有出发地后才显示 Live Trip 旅行板。
        findViewById(R.id.tripBoard).setVisibility(state.getDeparture().isEmpty() ? View.GONE : View.VISIBLE);
        // 只有出发地时显示 Depart from；两地都有时用格式化文案显示 XXX → YYY。
        text(R.id.tripBoardDestination, state.getDestination().isEmpty()
                ? getString(R.string.depart_from, state.getDeparture())
                : getString(R.string.trip_route, state.getDeparture(), state.getDestination()));
        // 从左往右找第一个符合的情况：出发地已确认 → 位置已确认 → 日期摘要/待选日期。
        // 嵌套三元表达式就是紧凑写法的 if / else if / else，不会同时选择多条文案。
        String status = state.getDestination().isEmpty() ? getString(R.string.departure_confirmed)
                : step == 1 ? getString(R.string.location_confirmed)
                : state.isStepComplete(2) ? form.dateSummary() : getString(R.string.date_start_prompt);
        // 将刚算出的状态文字写入旅行板的副标题控件。
        text(R.id.tripBoardStatus, status);
        // next 这个局部变量是按钮对象，和下面名为 next() 的方法不是同一回事。
        MaterialButton next = findViewById(R.id.nextButton);
        // 让 state 判断答案是否完整；未完成时按钮不可点击，外观由禁用态样式控制。
        next.setEnabled(state.canGoNext());
        // INVISIBLE 隐藏但保留位置，避免右箭头消失后中间的步骤编号发生偏移。
        next.setVisibility(generation ? View.INVISIBLE : View.VISIBLE);
        // 无障碍读屏描述：第 5 步右箭头代表生成，其余步骤代表前进。
        next.setContentDescription(getString(step == 5 ? R.string.generate_action : R.string.navigation_next));
        // 第一页也允许点左箭头，因为这时含义是退出规划、返回首页。
        findViewById(R.id.backButton).setEnabled(true);
        // 按当前是否还能退一步，给左箭头设置准确的读屏文字。
        findViewById(R.id.backButton).setContentDescription(getString(
                state.canGoBack() ? R.string.navigation_back : R.string.back_home));
        // 第 1 步禁用自定义系统返回回调，让系统默认返回；后续步骤则由 back() 管理。
        backCallback.setEnabled(state.canGoBack());
        // 让动态渲染器重画当前步骤的城市、日历或选项卡。
        form.render();
        // 生成页不接收表单答案，因此移除底部输入区域及其占位。
        findViewById(R.id.composerContainer).setVisibility(generation ? View.GONE : View.VISIBLE);
        // 清除输入框上一次的错误标记；null 在这里表示“没有错误”。
        input.setError(null);
        // hint 是空输入框中的提示，不是用户答案；第 1 步目的地有单独的提示文案。
        input.setHint(step == 1 && !editingDeparture ? getString(R.string.destination_hint)
                : getResources().getStringArray(R.array.planner_hints)[step - 1]);
        // render 会重填输入框，因此触发重绘前需要先 saveDraft，避免覆盖尚未提交的输入。
        input.setText(state.getDraft(step));
        // 把光标放到文本末尾，方便用户接着写；length 返回当前文本长度。
        input.setSelection(input.length());
        // 旧的临时反馈默认隐藏；需要新反馈的方法会在 render 后重新显示它。
        feedback.setVisibility(View.GONE);
        // post 等控件重绘/布局后再滚动；() -> ... 表示此任务不接收参数。
        if (scrollToTop) scroll.post(() -> scroll.smoothScrollTo(0, 0));
        // 只有生成中才安排演示完成任务；普通填写和已完成状态不安排。
        if (state.isGenerating()) scheduleMock();
    }

    // Actions 回调：用户点了表单卡片，渲染器已更新 state，再通知此处刷新整页。
    @Override public void changed() {
        // 重绘前备份输入框文字，不能让一次卡片点击抹掉用户正在输入的补充说明。
        saveDraft();
        // false 保持当前位置，不因为选择一张卡片就跳回页面顶部。
        render(false);
    }

    // 用户点击推荐出发城市时执行；city 是卡片传来的城市名称，如 "Sydney"。
    @Override public void chooseDeparture(String city) {
        // 将城市写入数据对象，供 Live Trip 和后续生成使用。
        state.selectDeparture(city);
        // 仍在第 1 步，但输入框下一次发送应作为目的地，而不是再次修改出发地。
        editingDeparture = false;
        // 已选好出发地，清空当前输入控件，避免出发地文字被误提交为目的地。
        input.setText("");
        // 同步清空 state 中第 1 步草稿，否则 render 又会把旧文字填回来。
        state.setDraft(1, "");
        // 刷新旅行板和推荐目的地，保留滚动位置。
        render(false);
    }

    // 用户点“更改出发地/目的地”时执行；departure=true 改出发地，false 改目的地。
    @Override public void editLocation(boolean departure) {
        // 先把现有输入留在草稿里，改变编辑模式不会立即提交或丢弃文字。
        saveDraft();
        // 记录输入框下一次提交时应该写入哪个位置字段。
        editingDeparture = departure;
        // 更新输入提示和 AI 提示，让用户知道现在正在改什么。
        render(false);
        // 请求输入焦点；仅有焦点并不保证软键盘已经弹出，因此下面显式请求显示键盘。
        input.requestFocus();
        // 等待本轮布局后再读取坐标，避免使用重绘前的旧位置。
        input.post(() -> {
            // input.getBottom() 相对其父容器；加上输入区顶部位置，用来将滚动位置移向输入框。
            scroll.smoothScrollTo(0, input.getBottom() + findViewById(R.id.composerContainer).getTop());
            // getWindow 是当前页面窗口；show(ime) 请求显示软键盘。
            new WindowInsetsControllerCompat(getWindow(), input).show(WindowInsetsCompat.Type.ime());
        });
    }

    // 日历点选回调；date 例如 20261110，是纯日期整数，不是时间戳。
    @Override public void dateSelected(int date) {
        // 提前记录是否“起点已选、终点未选、这次点得更早”；更新 state 后就无法这样判断了。
        boolean reset = state.getEndDate() == 0 && state.getStartDate() != 0 && date < state.getStartDate();
        // 真正的区间选择规则放在 TripPlanState，此页只把点击日期传过去。
        state.selectDate(date);
        // 保存输入草稿并重画日历，使选中颜色和日期说明同步变化。
        changed();
        // 必须在 changed/render 之后显示，否则 render 会立即把这条反馈隐藏。
        if (reset) showFeedback(R.string.date_restarted);
    }

    /** 将输入交给当前步骤：位置/同行人/预算是答案，日期和限制步骤的文字是补充说明。 */
    private void submitMessage() {
        // getText 返回可编辑文本对象；可能为 null，先兜底为空串，再去除首尾空格。
        String message = input.getText() == null ? "" : input.getText().toString().trim();
        // 空白输入：在输入框标记错误并立即 return，不再执行本方法后续提交逻辑。
        if (message.isEmpty()) { input.setError(getString(R.string.empty_message)); return; }
        // 读取提交发生时的步骤号，同一输入框会随步骤承担不同任务。
        int step = state.getCurrentStep();
        // 第 1 步包含两个字段，需要再通过 editingDeparture 区分当前填哪个。
        if (step == 1) {
            if (editingDeparture) {
                // 把自定义输入记为出发地，如用户输入列表外的城市。
                state.selectDeparture(message);
                // 填完出发地后，下一次输入切换为目的地。
                editingDeparture = false;
            } else {
                // equalsIgnoreCase 忽略大小写：Sydney 和 sydney 仍视为相同城市。
                if (message.equalsIgnoreCase(state.getDeparture())) {
                    // 保留用户文字并显示错误，方便直接修正。
                    input.setError(getString(R.string.same_city_error));
                    // 结束本次提交；不会清空输入，也不会覆盖原目的地。
                    return;
                }
                // 有效目的地写入 state；若城市改变，state 内部还会清理旧预算。
                state.selectDestination(message);
            }
        // 第 3 步自定义同行人答案，例如 "Three friends"；与点击预设卡片写入同一字段。
        } else if (step == 3) state.setCompanion(message);
        // 第 4 步自定义预算文本只作为答案保存，这里不解析金额或进行货币换算。
        else if (step == 4) state.setBudget(message);
        // 剩余第 2/5 步把文字存为备注；日期备注不等于已选好日期或已启用灵活日期。
        else state.setNote(step, message);
        // 成功提交后清除该步骤的“未提交草稿”，正式答案已经写入对应字段。
        state.setDraft(step, "");
        // 同时清空屏幕输入控件，而不只是数据对象中的草稿。
        input.setText("");
        // 提交完成，收起软键盘，给选项区更多可见空间。
        hideKeyboard();
        // 用最新答案刷新选中卡片、旅行板和下一步按钮是否可用。
        render(false);
        // 第 2 步明确提示日期备注已保存；其他非位置步骤显示通用保存提示。
        if (step > 1) showFeedback(step == 2 ? R.string.date_notes_saved : R.string.notes_saved);
        // 修改出发地后也可能与已有目的地相同；此处提示，state 会阻止继续前进。
        if (step == 1 && state.getDeparture().equalsIgnoreCase(state.getDestination())) {
            showFeedback(R.string.same_city_error);
        }
    }

    // 屏幕右箭头和最后的生成按钮共用这一条路径，不能绕过输入提交与完整性检查。
    private void next() {
        // 输入框非空时先尝试提交，避免用户没点发送就前进导致文字丢失。
        if (input.length() > 0) submitMessage();
        // ! 表示“不满足”，|| 表示“任一成立”；答案不完整或输入仍报错，就留在本步。
        if (!state.canGoNext() || input.getError() != null) return;
        // 切换步骤前关闭当前键盘。
        hideKeyboard();
        // 1～4 步会步数加一；第 5 步则把状态切换成“生成中”。
        state.goNext();
        // 只有从最后一步进入生成时，才开始新的生成 ID 和保存状态。
        if (state.isGenerating()) {
            // 随机唯一标识转成字符串，后续保存/打开行程都依靠这个 ID。
            generationId = UUID.randomUUID().toString();
            // 新一轮不能沿用上一轮“已保存”标记。
            saveStatus = 0;
        }
        // 新步骤从顶部开始显示，生成中还会安排 finishMock 任务。
        render(true);
    }

    // Actions 的返回实现，也被页面按钮和系统返回回调复用。
    @Override public void back() {
        // 第 1 步无上一步：finish 关闭当前 Activity；return 避免继续执行下面的退步逻辑。
        if (!state.canGoBack()) { finish(); return; }
        // 后退只暂存文字，不把尚未发送的输入当作正式答案。
        saveDraft();
        // 避免下一页仍被上一页键盘遮挡。
        hideKeyboard();
        // 返回表单后取消延迟回调，避免演示生成突然把用户带回结果页。
        handler.removeCallbacks(finishMock);
        // 生成页回到第 5 步；普通表单则减少一步。已有答案不因此清空。
        state.goBack();
        // 重画退回的步骤并滚动到顶部。
        render(true);
    }

    // 保存的是当前步骤输入草稿，不是正式行程，也不会写磁盘。
    private void saveDraft() {
        // && 表示两者都不处于：只有填写表单时输入框才有需要保存的有效草稿。
        if (!state.isGenerating() && !state.isGenerated())
            // 保留原文字（包括空格），回到该步骤时可以按原样继续编辑。
            state.setDraft(state.getCurrentStep(), input.getText() == null ? "" : input.getText().toString());
    }
    // 不清空输入内容，只取消输入焦点并请求收起输入法。
    private void hideKeyboard() {
        // clearFocus 不等于 clear 文本；用户已写的文字仍然保留。
        input.clearFocus();
        // 通过兼容控制器操作当前窗口的软键盘，ime 与 onCreate 中的含义相同。
        new WindowInsetsControllerCompat(getWindow(), input).hide(WindowInsetsCompat.Type.ime());
    }
    // 根据 ID 找控件，再强制转为 TextView 并设置文字；调用时必须传 TextView 类控件的 ID。
    private void text(int id, String value) { ((TextView) findViewById(id)).setText(value); }
    // id 是 R.string 文案资源编号，不是控件编号。
    private void showFeedback(int id) {
        // TextView.setText(int) 会读取文案资源，而不是把这个整数本身显示出来。
        feedback.setText(id);
        // 把 render 中隐藏的反馈区域重新显示出来。
        feedback.setVisibility(View.VISIBLE);
    }
    private void scheduleMock() {
        // 先移除同一个任务再安排，避免重复 render 导致多次“生成完成”。
        handler.removeCallbacks(finishMock);
        // 1200 毫秒=1.2 秒后排队执行；不让线程睡眠，也没有发起任何模型接口请求。
        handler.postDelayed(finishMock, 1200);
    }
    // 将保存状态提供给 PlannerFormRenderer，供它选择“保存中/重试/打开行程”等 UI。
    @Override public int saveStatus() { return saveStatus; }
    // 结果页自动保存以及用户点“重试保存”都进入此方法。
    @Override public void saveTrip() {
        // 未生成完成时直接退出，不能把半填表单伪装成已生成的行程保存。
        if (!state.isGenerated()) return;
        // 正常流程已有 ID；恢复页面等场景缺少 ID 时在此补上。
        if (generationId == null) generationId = UUID.randomUUID().toString();
        // 将本次 ID 留在局部变量，异步结束后与最新 generationId 对照，识别过期结果。
        String savingId = generationId;
        // 先生成快照再交给后台线程，后续修改表单不会改动这次待保存的内容。
        SavedTrip snapshot;
        // this 提供 Android 资源访问；fromPlan 读取 state，构造独立的 SavedTrip 快照。
        try { snapshot = SavedTrip.fromPlan(this, state, savingId); }
        // 构造失败就标记失败、刷新重试入口并结束；不会提交一个不完整的快照给存储层。
        catch (IllegalArgumentException error) { saveStatus = 2; render(false); return; }
        // Application Context 不依附此 Activity 的窗口，适合后台写入任务使用。
        TripStore store = new TripStore(getApplicationContext());
        // 写入尚未完成，先告诉界面正在保存。
        saveStatus = 0;
        // 即刻显示保存中，不必等磁盘任务返回后才更新页面。
        render(false);
        // 将 lambda 中的写入工作提交到后台单线程；调用这里不会在 UI 线程等待磁盘。
        saveExecutor.execute(() -> {
            // save 返回 true 才确认本机写入成功，false 则表示需要重试。
            boolean saved = store.save(snapshot);
            // Android 控件只能在 UI 线程更新，后台线程拿到结果后把处理交回主线程。
            runOnUiThread(() -> {
                // 异步结果可能晚于页面销毁或新一轮生成，旧结果不能覆盖新的 UI 状态。
                if (isDestroyed() || !savingId.equals(generationId) || !state.isGenerated()) return;
                // 布尔结果转为本页约定的状态码：成功 1，失败 2。
                saveStatus = saved ? 1 : 2;
                // 重画结果页，使成功时出现打开按钮、失败时出现重试按钮。
                render(false);
            });
        });
    }
    @Override public void viewSavedTrips() {
        // Intent 指向 HomeActivity，putExtra 传布尔参数，让首页选中 My Trips 栏目。
        // CLEAR_TOP 移除已有首页上方的页面；SINGLE_TOP 让已有首页通过 onNewIntent 接收参数。
        startActivity(new Intent(this, HomeActivity.class).putExtra(HomeActivity.EXTRA_OPEN_MY, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        // 结束当前规划页，避免从行程库返回时又看到已完成的生成页。
        finish();
    }
    @Override public void viewItinerary() {
        // 只有确认落盘且 ID 有效才打开详情；只传 ID，详情页自己从 TripStore 读取 JSON。
        if (saveStatus == 1 && generationId != null) startActivity(new Intent(this, TripDetailActivity.class)
                .putExtra(TripDetailActivity.EXTRA_ID, generationId));
    }
    // 此 Activity 实例销毁时调用；可能是用户离开，也可能是旋转等导致页面重建。
    @Override protected void onDestroy() {
        // 取消尚未执行的演示任务，防止任务继续引用旧页面。
        handler.removeCallbacks(finishMock);
        // 不再接受新任务，但已排队的保存继续完成；不是 shutdownNow，不强行中断已有写入。
        saveExecutor.shutdown();
        // 最后交给父类完成生命周期清理。
        super.onDestroy();
    }
    // 页面进入可见状态时调用；从后台回来也可能再次进入此方法。
    @Override protected void onStart() {
        // 先执行父类的可见状态处理。
        super.onStart();
        // 演示仍未完成时重新安排 1.2 秒计时；这里没有恢复精确的剩余倒计时。
        if (state.isGenerating()) scheduleMock();
    }
    // 页面不再可见时调用；不是每次 onStop 都会马上 onDestroy。
    @Override protected void onStop() {
        // 不在不可见时触发演示完成；再回到前台由 onStart 重新安排。
        handler.removeCallbacks(finishMock);
        // 让父类完成停止阶段的处理。
        super.onStop();
    }
    // Android 需要保留此页状态以便之后重建时调用；不是可靠的长期数据库保存入口。
    @Override protected void onSaveInstanceState(Bundle outState) {
        // 将已输入但未发送的文字一起恢复；与“正式保存行程”是两条独立路径。
        saveDraft();
        // TripPlanState 实现 Serializable，所以可以把整个表单对象放入 Bundle。
        outState.putSerializable("trip_state", state);
        // 下列键名与 onCreate 的 getString/getInt/getBoolean 对应，拼写必须一致。
        outState.putString("generation_id", generationId);
        // 保留是否已成功保存，重建后决定要不要重试写入。
        outState.putInt("trip_save_status", saveStatus);
        // 保留第一步输入框正在改出发地还是目的地。
        outState.putBoolean("editing_departure", editingDeparture);
        // 只存一个轻量日期整数，不把 Calendar 对象直接传入 Bundle。
        outState.putInt("calendar_month", TripDates.key(calendarMonth));
        // 父类还会保存它管理的控件状态，不能省略父类调用。
        super.onSaveInstanceState(outState);
    }
}
