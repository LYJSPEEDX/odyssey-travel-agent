package com.example.odyssey;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import java.util.ArrayList;
import java.util.List;

/** 行程摘要的卡片视图：只读取 state 展示，不修改答案；保存和跳转仍由 PlannerFormRenderer 负责。 */
final class TripBriefView {
    /** showRoute 为 false 时顶部行程卡已显示路线和日期，这里不再重复。 */
    static void add(LinearLayout content, TripPlanState state, String dates, boolean showRoute) {
        Context context = content.getContext();
        if (showRoute) {
            MaterialCardView route = tile(context, context.getString(R.string.trip_route, state.getDeparture(), state.getDestination()),
                    "", dates, R.color.trip_board, R.color.on_trip_board);
            content.addView(route, margins(context, new LinearLayout.LayoutParams(-1, -2)));
        }
        String budget = TripOptions.budgetDescription(state);
        int split = budget.indexOf(" · ");
        LinearLayout row = new LinearLayout(context);
        row.setBaselineAligned(false);
        row.addView(tile(context, context.getString(R.string.brief_with), state.getCompanion(), "",
                R.color.mint, R.color.on_accent), margins(context, new LinearLayout.LayoutParams(0, -1, 1f)));
        LinearLayout.LayoutParams second = margins(context, new LinearLayout.LayoutParams(0, -1, 1f));
        second.setMarginStart(dp(context, 10));
        row.addView(tile(context, context.getString(R.string.brief_budget), split > 0 ? budget.substring(0, split) : budget,
                split > 0 ? context.getString(R.string.brief_budget_unit) : "", R.color.peach, R.color.on_accent), second);
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));

        MaterialCardView card = card(context, R.color.surface);
        LinearLayout body = body(context);
        body.addView(label(context, context.getString(R.string.brief_preferences), R.color.text_secondary));
        ChipGroup pills = new ChipGroup(context);
        pills.setChipSpacingHorizontal(dp(context, 8)); pills.setChipSpacingVertical(dp(context, 8));
        pills.setPadding(0, dp(context, 12), 0, 0);
        for (String value : preferences(context, state)) pills.addView(pill(context, value));
        body.addView(pills);
        card.addView(body);
        content.addView(card, margins(context, new LinearLayout.LayoutParams(-1, -2)));
    }
    /** 备注形如 “Pace: Packed pace; Experience: Local discoveries”，拆开后只留取值。 */
    private static List<String> preferences(Context context, TripPlanState state) {
        List<String> values = new ArrayList<>(state.getConstraints());
        for (int step = 1; step <= 5; step++)
            for (String part : state.getNote(step).split(";")) {
                String value = part.substring(part.indexOf(':') + 1).trim();
                if (!value.isEmpty()) values.add(value);
            }
        if (values.isEmpty()) values.add(context.getString(R.string.no_restrictions));
        return values;
    }
    private static MaterialCardView tile(Context context, String title, String value, String caption, int background, int foreground) {
        MaterialCardView card = card(context, background);
        LinearLayout body = body(context);
        boolean labelled = !value.isEmpty();
        if (labelled) body.addView(label(context, title, foreground));
        TextView main = text(context, labelled ? value : title, labelled ? 20 : 24, foreground);
        AppFonts.style(main, Typeface.BOLD);
        main.setPadding(0, labelled ? dp(context, 10) : 0, 0, 0);
        body.addView(main);
        if (!caption.isEmpty()) {
            TextView small = text(context, caption, 12, foreground);
            small.setPadding(0, dp(context, 6), 0, 0);
            body.addView(small);
        }
        card.addView(body);
        return card;
    }
    private static Chip pill(Context context, String value) {
        Chip chip = new Chip(context);
        chip.setText(value); chip.setClickable(false); chip.setCheckable(false); chip.setFocusable(false);
        chip.setEnsureMinTouchTargetSize(false); chip.setChipMinHeight(dp(context, 32));
        chip.setChipStrokeWidth(0); chip.setTextSize(13);
        chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.purple)));
        chip.setTextColor(ContextCompat.getColor(context, R.color.on_accent));
        chip.setShapeAppearanceModel(chip.getShapeAppearanceModel().toBuilder().setAllCornerSizes(dp(context, 16)).build());
        AppFonts.style(chip, Typeface.BOLD);
        return chip;
    }
    private static MaterialCardView card(Context context, int background) {
        MaterialCardView card = new MaterialCardView(context);
        card.setRadius(dp(context, 22)); card.setCardElevation(0); card.setStrokeWidth(0);
        card.setCardBackgroundColor(ContextCompat.getColor(context, background));
        return card;
    }
    private static LinearLayout body(Context context) {
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL); body.setGravity(Gravity.START);
        body.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16));
        return body;
    }
    private static TextView label(Context context, String value, int color) {
        TextView label = text(context, value, 11, color);
        label.setLetterSpacing(0.1f); AppFonts.style(label, Typeface.BOLD);
        return label;
    }
    private static TextView text(Context context, String value, int size, int color) {
        TextView text = new TextView(context);
        text.setText(value); text.setTextSize(size); text.setIncludeFontPadding(false);
        text.setTextColor(ContextCompat.getColor(context, color));
        return text;
    }
    private static LinearLayout.LayoutParams margins(Context context, LinearLayout.LayoutParams params) {
        params.topMargin = dp(context, 6); params.bottomMargin = dp(context, 6);
        return params;
    }
    private static int dp(Context context, int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private TripBriefView() {}
}
