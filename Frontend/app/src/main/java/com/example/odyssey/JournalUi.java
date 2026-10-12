package com.example.odyssey;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import com.google.android.material.card.MaterialCardView;

/** Journal-specific composition; the home and five-step planner keep their own styling. */
final class JournalUi {
    final HomeUi ui;
    JournalUi(HomeUi ui) { this.ui = ui; }
    boolean stacked() {
        android.content.res.Configuration config = ui.context.getResources().getConfiguration();
        return config.fontScale > 1.3f || config.screenWidthDp < 360;
    }
    LinearLayout column() {
        LinearLayout result = new LinearLayout(ui.context); result.setOrientation(LinearLayout.VERTICAL); return result;
    }
    LinearLayout row() {
        LinearLayout result = new LinearLayout(ui.context); result.setGravity(Gravity.CENTER_VERTICAL); return result;
    }
    TextView text(LinearLayout parent, String value, int size, boolean bold) {
        TextView view = new TextView(ui.context);
        view.setText(value); view.setTextSize(size); view.setIncludeFontPadding(false);
        view.setTextColor(ui.color(bold ? R.color.text_primary : R.color.text_secondary));
        view.setLineSpacing(ui.dp(3), 1);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = ui.dp(6);
        parent.addView(view, params); return view;
    }
    TextView heading(LinearLayout parent, String value, int size) {
        TextView view = text(parent, value, size, true); ViewCompat.setAccessibilityHeading(view, true); return view;
    }
    com.google.android.material.button.MaterialButton textAction(LinearLayout parent, String value, Runnable click) {
        com.google.android.material.button.MaterialButton button = new com.google.android.material.button.MaterialButton(ui.context);
        button.setText(value); button.setAllCaps(false); button.setTextSize(12);
        button.setMinHeight(ui.dp(48)); button.setMinWidth(0);
        button.setPadding(ui.dp(8), 0, ui.dp(8), 0);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
        button.setTextColor(ui.color(R.color.text_primary)); button.setElevation(0);
        button.setOnClickListener(view -> click.run());
        parent.addView(button, new LinearLayout.LayoutParams(-2, -2)); return button;
    }
    TextView tag(LinearLayout parent, String value, int background) {
        TextView view = text(parent, value, 11, true);
        view.setTextColor(ui.color(background == R.color.surface || background == R.color.background ? R.color.text_secondary : R.color.on_accent));
        view.setPadding(ui.dp(10), ui.dp(5), ui.dp(10), ui.dp(5));
        view.setBackground(shape(background, 10));
        view.setLayoutParams(new LinearLayout.LayoutParams(-2, -2)); return view;
    }
    GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(ui.color(color)); drawable.setCornerRadius(ui.dp(radius)); return drawable;
    }
    LinearLayout card(LinearLayout parent, int background, Runnable click) {
        MaterialCardView card = new MaterialCardView(ui.context);
        card.setRadius(ui.dp(20)); card.setCardElevation(0); card.setStrokeWidth(0);
        card.setCardBackgroundColor(ui.color(background)); card.setMinimumHeight(ui.dp(48));
        LinearLayout body = column(); body.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(10));
        card.addView(body, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = ui.dp(12);
        parent.addView(card, params);
        if (click != null) { card.setClickable(true); card.setFocusable(true); card.setOnClickListener(view -> click.run()); }
        return body;
    }
    LinearLayout timeline(String time) {
        LinearLayout row = new LinearLayout(ui.context); row.setTag("timeline-row");
        if (stacked()) {
            row.setOrientation(LinearLayout.VERTICAL);
            TextView stamp = text(row, "•  " + time, 12, true);
            stamp.setTypeface(Typeface.MONOSPACE);
            LinearLayout body = column(); row.addView(body, new LinearLayout.LayoutParams(-1, -2));
            ui.content.addView(row, new LinearLayout.LayoutParams(-1, -2)); return body;
        }
        LinearLayout axis = column(); axis.setGravity(Gravity.CENTER_HORIZONTAL); axis.setPadding(0, ui.dp(5), ui.dp(8), 0);
        TextView stamp = text(axis, time, 12, true); stamp.setGravity(Gravity.CENTER); stamp.setTypeface(Typeface.MONOSPACE);
        View dot = new View(ui.context); dot.setBackground(shape(R.color.text_primary, 6));
        axis.addView(dot, new LinearLayout.LayoutParams(ui.dp(6), ui.dp(6)));
        View line = new View(ui.context); line.setBackgroundColor(ui.color(R.color.outline));
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(ui.dp(1), 0, 1); lineParams.topMargin = ui.dp(5);
        axis.addView(line, lineParams);
        row.addView(axis, new LinearLayout.LayoutParams(ui.dp(56), -1));
        LinearLayout body = column(); row.addView(body, new LinearLayout.LayoutParams(0, -2, 1));
        ui.content.addView(row, new LinearLayout.LayoutParams(-1, -2)); return body;
    }
    LinearLayout rail(String tag) {
        HorizontalScrollView scroll = new HorizontalScrollView(ui.context); scroll.setTag(tag);
        scroll.setHorizontalScrollBarEnabled(false); scroll.setClipToPadding(false);
        LinearLayout row = new LinearLayout(ui.context); scroll.addView(row);
        ui.content.addView(scroll, new LinearLayout.LayoutParams(-1, -2)); return row;
    }
    void rule(LinearLayout parent) {
        View rule = new View(ui.context); rule.setBackgroundColor(ui.color(R.color.outline));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, ui.dp(1));
        params.topMargin = ui.dp(8); params.bottomMargin = ui.dp(12); parent.addView(rule, params);
    }
    void disclosure(String title, String subtitle, Runnable click) {
        LinearLayout body = card(ui.content, R.color.surface, click);
        text(body, title + "  ↗", 16, true);
        if (!subtitle.isEmpty()) text(body, subtitle, 12, false);
    }
}
