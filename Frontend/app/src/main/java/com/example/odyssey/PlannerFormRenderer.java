package com.example.odyssey;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.util.Calendar;

/**
 * 按当前步骤构建动态表单，只管展示；答案与完成条件由 TripPlanState 管理。
 * 点击先更新 state，再通过 Actions 通知 MainActivity 刷新标题、进度条等固定区域。
 */
final class PlannerFormRenderer {
    // 回调接口让渲染器不必直接持有 MainActivity，也不负责页面跳转或存储实现。
    interface Actions {
        // 修改答案后请 Activity 重画固定区域和表单；不表示发起网络请求。
        void changed();
        // 选择出发地还要切换输入模式，所以由 Activity 统一完成。
        void chooseDeparture(String city);
        // true 编辑出发地，false 编辑目的地。
        void editLocation(boolean departure);
        // 日期以 YYYYMMDD 整数传出；真正选区间的规则在 TripPlanState。
        void dateSelected(int date);
        void back();
        int saveStatus();
        void saveTrip();
        void viewSavedTrips();
        void viewItinerary();
        default String generationError() { return ""; }
        default void retryGeneration() { }
    }

    // Context 提供资源、主题等环境；content 是 XML 的 stepContent 容器。
    private final Context context;
    private final LinearLayout content;
    private final TripPlanState state;
    private final Calendar month;
    private final Actions actions;

    PlannerFormRenderer(LinearLayout content, TripPlanState state, Calendar month, Actions actions) {
        // 构造器保存传入对象的引用；this.state 与 MainActivity.state 指向同一个表单对象。
        // this.content 是成员字段，右侧 content 是构造器参数，用 this 区分同名变量。
        this.context = content.getContext();
        this.content = content;
        this.state = state;
        this.month = month;
        this.actions = actions;
    }

    void render() {
        // 每次重建当前步骤，所以选择状态必须从 state 读取，不能只留在旧卡片上。
        content.removeAllViews();
        // 生成区域单独渲染并 return，避免同时绘制第五步的限制选项。
        if (state.isGenerating() || state.isGenerated()) { generation(); return; }
        // switch 选择当前步骤；break 结束本分支，不继续执行下一步的绘制方法。
        switch (state.getCurrentStep()) {
            case 1: location(); break;
            case 2: dates(); break;
            case 3: companions(); break;
            case 4: budgets(); break;
            case 5: constraints(); break;
            default: break;
        }
    }

    /** 同一步分两段：未选出发地时只展示出发地；选好后才展示目的地推荐。 */
    private void location() {
        label(R.string.departure_section);
        if (state.getDeparture().isEmpty()) {
            // Chip 是小胶囊按钮；ChipGroup 按可用宽度排列一组城市，可自动换行。
            ChipGroup origins = new ChipGroup(context);
            origins.setChipSpacingHorizontal(dp(8));
            for (String city : TripOptions.DEPARTURES) {
                // for-each 每次取一个城市，为它创建一颗独立按钮，而不是复用同一颗按钮。
                Chip chip = new Chip(context);
                chip.setText(city);
                // 外观可以紧凑，但可触摸区域仍需要达到组件的最小点击尺寸。
                chip.setEnsureMinTouchTargetSize(true);
                // valueOf 将单一色值包装为控件所需的 ColorStateList 类型。
                chip.setChipBackgroundColor(ColorStateList.valueOf(color(R.color.surface)));
                chip.setTextColor(color(R.color.text_primary));
                // 等点击时把本次循环的城市传给 Activity；设置监听器时不会立即选择城市。
                chip.setOnClickListener(view -> actions.chooseDeparture(city));
                origins.addView(chip);
            }
            content.addView(origins);
            paragraph(context.getString(R.string.choose_departure_first));
            // 未选择出发地就到此为止；下面的目的地卡片暂时不生成。
            return;
        }
        option(state.getDeparture(), context.getString(R.string.change_departure), true,
                () -> actions.editLocation(true));
        label(R.string.destination_section);
        // 图片网格只负责展示；回调依旧更新同一份业务 state，五步流程不变。
        DestinationCards.add(content, state, city -> {
            state.selectDestination(city);
            actions.changed();
        });
        // 用户输入目录外城市时，额外显示自己的选择，否则选中了也看不到对应卡片。
        if (!state.getDestination().isEmpty() && TripOptions.destination(state.getDestination()) == null) {
            option(state.getDestination(), context.getString(R.string.custom_city_description), true,
                    () -> actions.editLocation(false));
        }
        button(R.string.change_destination, () -> actions.editLocation(false));
    }

    private void dates() {
        label(R.string.date_section);
        // 依次判断：日期灵活 → 还没起点 → 等终点 → 完整区间，生成对应引导文字。
        // getQuantityString 按数量选英文单复数模板，后面的参数再填进模板占位符。
        String status = state.isFlexibleDates() ? context.getString(R.string.flexible_dates_summary)
                : state.getStartDate() == 0 ? context.getString(R.string.date_start_prompt)
                : state.getEndDate() == 0 ? context.getString(R.string.date_end_prompt,
                        TripDates.format(state.getStartDate()))
                : context.getResources().getQuantityString(R.plurals.date_range_complete,
                        state.getDurationDays(), dateSummary(), state.getDurationDays());
        paragraph(status);
        if (!state.isFlexibleDates() && state.getEndDate() != 0 && state.getDurationDays() > 60)
            paragraph(context.getString(R.string.itinerary_duration_limit));
        // actions::dateSelected 是方法引用，等价于 date -> actions.dateSelected(date)。
        // 最后 () -> {} 是空回调：翻月由日历自身重画，此处不用额外刷新整页。
        content.addView(new TripCalendarView(context, state, month, actions::dateSelected, () -> {}));
        paragraph(context.getString(R.string.calendar_legend));
        option(context.getString(R.string.flexible_dates),
                context.getString(R.string.flexible_dates_description), state.isFlexibleDates(), () -> {
                    state.useFlexibleDates();
                    actions.changed();
                });
        // 把第 2 步已提交的文字备注显示出来；它不会替代日历选区。
        savedNote(2);
    }

    private void companions() {
        label(R.string.companion_section);
        // 用来记录用户答案是否匹配任何一个预设；都不匹配时需要显示自定义答案卡。
        boolean known = false;
        for (TripOptions.Option option : TripOptions.COMPANIONS) {
            boolean selected = option.title.equals(state.getCompanion());
            // 对 boolean 而言，相当于 known = known | selected；只要命中过一次就保持 true。
            known |= selected;
            option(option.title, option.description, selected, () -> {
                state.setCompanion(option.title);
                actions.changed();
            });
        }
        if (!known && !state.getCompanion().isEmpty()) {
            // null 表示这张卡只展示已填答案，不绑定点击操作；修改仍从输入框进行。
            option(context.getString(R.string.custom_option), state.getCompanion(), true, null);
        }
    }

    private void budgets() {
        label(R.string.budget_section);
        paragraph(context.getString(R.string.budget_disclaimer));
        if (TripOptions.destination(state.getDestination()) == null) {
            paragraph(context.getString(R.string.generic_budget_disclaimer));
        }
        boolean known = false;
        // 每次按当前目的地拿三档假预算；改城市后不能复用旧城市的档位说明。
        for (TripOptions.Option option : TripOptions.budgets(state.getDestination())) {
            boolean selected = option.title.equals(state.getBudget());
            known |= selected;
            option(option.title, option.description, selected, () -> {
                state.setBudget(option.title);
                actions.changed();
            });
        }
        if (!known && !state.getBudget().isEmpty()) {
            option(context.getString(R.string.custom_option), state.getBudget(), true, null);
        }
    }

    /** 限制允许多选；有冲突时提示用户，但不悄悄删除其中一个偏好。 */
    private void constraints() {
        label(R.string.constraints_section);
        for (TripOptions.Option option : TripOptions.CONSTRAINTS) {
            // contains 判断集合是否含此限制；与同行人单选不同，这里允许多张卡同时选中。
            option(option.title, option.description, state.getConstraints().contains(option.title), () -> {
                state.toggleConstraint(option.title);
                actions.changed();
            });
        }
        option(context.getString(R.string.no_restrictions),
                context.getString(R.string.no_restrictions_description),
                state.getConstraints().isEmpty() && state.getNote(5).isEmpty(), () -> {
                    state.clearConstraints();
                    actions.changed();
                });
        if (state.getConstraints().contains("See more") && state.getConstraints().contains("Take it slow")) {
            paragraph(context.getString(R.string.pace_conflict));
        }
        savedNote(5);
        button(R.string.generate_action, () -> {
            // 从整页根 View 找到顶部下一步按钮，而非只在 stepContent 内查找。
            View next = content.getRootView().findViewById(R.id.nextButton);
            // 程序触发其点击逻辑，复用 MainActivity.next()，包括提交未发送文字和校验。
            next.performClick();
        });
    }

    /** 区分“生成完毕”与“写入成功”：只有成功落盘，才提供打开已保存行程的入口。 */
    private void generation() {
        if (state.isGenerating() && !actions.generationError().isEmpty()) {
            paragraph(actions.generationError());
            button(R.string.generation_retry, actions::retryGeneration);
            button(R.string.edit_trip, actions::back);
        }
        if (state.isGenerating() && actions.generationError().isEmpty()) {
            LinearProgressIndicator progress = new LinearProgressIndicator(context);
            // indeterminate 是不确定进度动画：这里只模拟等待，不伪造真实百分比。
            progress.setIndeterminate(true);
            progress.setIndicatorColor(color(R.color.lime));
            progress.setTrackColor(color(R.color.outline));
            content.addView(progress, new LinearLayout.LayoutParams(-1, dp(4)));
        }
        label(R.string.trip_summary);
        paragraph(context.getString(R.string.summary_route,
                context.getString(R.string.trip_route, state.getDeparture(), state.getDestination())));
        paragraph(context.getString(R.string.summary_dates, dateSummary()));
        paragraph(context.getString(R.string.summary_companion, state.getCompanion()));
        String budget = TripOptions.budgetDescription(state);
        paragraph(context.getString(R.string.summary_budget, budget));
        paragraph(context.getString(budget.equals(state.getBudget())
                ? R.string.custom_budget_disclaimer : R.string.budget_disclaimer));
        paragraph(context.getString(R.string.summary_constraints, state.getConstraints().isEmpty()
                ? context.getString(R.string.no_restrictions) : String.join(" · ", state.getConstraints())));
        String[] steps = context.getResources().getStringArray(R.array.step_names);
        for (int step = 1; step <= 5; step++) {
            if (!state.getNote(step).isEmpty()) paragraph(context.getString(R.string.summary_note,
                    steps[step - 1], state.getNote(step)));
        }
        if (state.isGenerated()) {
            // Actions 查询 Activity 中真实的存储结果，而不是只看 state.isGenerated()。
            TextView saved = paragraph(context.getString(actions.saveStatus() == 1
                    ? R.string.trip_saved : actions.saveStatus() == 2
                    ? R.string.trip_save_failed : R.string.trip_saving));
            saved.setId(R.id.saveTripStatus);
            // 保存结果变化时，读屏工具可在合适时机播报，不强行打断当前朗读。
            saved.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            if (actions.saveStatus() == 1) {
                button(R.string.open_itinerary, actions::viewItinerary).setId(R.id.openItinerary);
                button(R.string.view_my_trips, actions::viewSavedTrips).setId(R.id.viewMyTrips);
            } else if (actions.saveStatus() == 2) {
                button(R.string.trip_save_retry, actions::saveTrip);
            }
            paragraph(context.getString(R.string.itinerary_draft_notice));
            button(R.string.edit_trip, actions::back);
        }
    }

    String dateSummary() {
        return state.isFlexibleDates() ? context.getString(R.string.flexible_dates_summary)
                : context.getString(R.string.date_range, TripDates.format(state.getStartDate()),
                        TripDates.format(state.getEndDate()));
    }

    private void savedNote(int step) {
        if (!state.getNote(step).isEmpty()) paragraph(context.getString(R.string.saved_note, state.getNote(step)));
    }
    private void option(String title, String description, boolean selected, Runnable action) {
        // inflate(..., false) 只创建 View，末尾 addView 才挂到父容器，避免重复添加。
        MaterialCardView card = (MaterialCardView) LayoutInflater.from(context)
                .inflate(R.layout.item_trip_option, content, false);
        // 在刚创建的卡片内部查找标题与说明；每张卡片都可以有这两个相同资源 ID。
        TextView heading = card.findViewById(R.id.optionTitle);
        TextView body = card.findViewById(R.id.optionDescription);
        // 选中时使用带选中标记的标题模板，未选中时直接显示原始标题。
        heading.setText(selected ? context.getString(R.string.selected_option, title) : title);
        body.setText(description);
        // 根据 selected 设置本次渲染的背景、文字和描边；不会因此自动更改 state。
        card.setCardBackgroundColor(color(selected ? R.color.lime : R.color.surface));
        heading.setTextColor(color(selected ? R.color.on_accent : R.color.text_primary));
        body.setTextColor(color(selected ? R.color.on_accent : R.color.text_secondary));
        card.setStrokeColor(color(selected ? R.color.on_accent : R.color.outline));
        // 同步控件自身的选中状态，供无障碍和状态样式读取。
        card.setSelected(selected);
        card.setContentDescription(context.getString(selected ? R.string.selected_accessibility
                : R.string.option_accessibility, title, description));
        card.setClickable(action != null);
        card.setFocusable(true);
        // Runnable.run 执行调用方传入的动作，这里不需要知道它是选择城市还是切换限制。
        if (action != null) card.setOnClickListener(view -> action.run());
        // new/inflate 只创建控件；addView 后它才成为页面的一部分。
        content.addView(card);
    }
    private void label(int string) {
        TextView label = paragraph(context.getString(string));
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        ViewCompat.setAccessibilityHeading(label, true);
    }
    private TextView paragraph(String text) {
        // 动态创建文字控件；没有单独的 XML，但仍使用当前主题的 Context 和颜色资源。
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(color(R.color.text_secondary));
        view.setTextSize(14);
        view.setPadding(0, dp(8), 0, dp(12));
        content.addView(view);
        return view;
    }
    private MaterialButton button(int title, Runnable action) {
        MaterialButton button = new MaterialButton(context);
        button.setText(title);
        // 不把英文标题自动转换为全大写。
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        button.setCornerRadius(dp(20));
        button.setBackgroundTintList(ColorStateList.valueOf(color(R.color.mint)));
        button.setTextColor(color(R.color.on_accent));
        button.setOnClickListener(view -> action.run());
        // 宽 -1=填满父容器，高 -2=根据文字/内边距自适应；不是负像素尺寸。
        content.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }
    private int color(int id) { return ContextCompat.getColor(context, id); }
    // Java 布局尺寸使用 px；设计尺寸先按屏幕密度从 dp 转换，字号 setTextSize 默认使用 sp。
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
