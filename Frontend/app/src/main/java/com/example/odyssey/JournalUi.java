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

/**
 * 行程手账专用组件：标题、标签、卡片、时间轴、横向酒店栏。
 * 这里只处理布局，不读取业务 JSON；与 HomeUi 分开，避免修改详情样式时影响五步表单。
 */
final class JournalUi {
    final HomeUi ui;
    JournalUi(HomeUi ui) { this.ui = ui; }
    boolean stacked() {
        // 窄屏/大字体改上下排列，不通过缩小字体硬塞横排，避免时间与按钮文字被裁切。
        android.content.res.Configuration config = ui.context.getResources().getConfiguration();
        // 1.3f 是 float 数字；字体超过 1.3 倍，或宽度小于 360dp，任一成立就竖排。
        return config.fontScale > 1.3f || config.screenWidthDp < 360;
    }
    LinearLayout column() {
        LinearLayout result = new LinearLayout(ui.context); result.setOrientation(LinearLayout.VERTICAL); return result;
    }
    LinearLayout row() {
        // LinearLayout 默认横排；gravity 控制子控件在内部的对齐，不是它相对外层的位置。
        LinearLayout result = new LinearLayout(ui.context); result.setGravity(Gravity.CENTER_VERTICAL); return result;
    }
    TextView text(LinearLayout parent, String value, int size, boolean bold) {
        TextView view = new TextView(ui.context);
        // 字号默认 sp，响应系统字号；false 去掉字体自带上下留白，便于视觉对齐。
        view.setText(value); view.setTextSize(size); view.setIncludeFontPadding(false);
        view.setTextColor(ui.color(bold ? R.color.text_primary : R.color.text_secondary));
        // 每行额外增加 3dp 行距，倍数 1 表示不再乘倍数拉大。
        view.setLineSpacing(ui.dp(3), 1);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = ui.dp(6);
        // 加入页面后返回控件引用，调用方仍可继续改 padding、颜色或最大行数。
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
        // 这里只设置纯色与圆角，等价于简单 XML shape 背景，没有真正使用渐变。
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(ui.color(color)); drawable.setCornerRadius(ui.dp(radius)); return drawable;
    }
    /** 返回卡片内部的内容容器，不是外层 MaterialCardView；调用方可继续向其添加文字。 */
    LinearLayout card(LinearLayout parent, int background, Runnable click) {
        MaterialCardView card = new MaterialCardView(ui.context);
        card.setRadius(ui.dp(20)); card.setCardElevation(0); card.setStrokeWidth(0);
        card.setCardBackgroundColor(ui.color(background)); card.setMinimumHeight(ui.dp(48));
        // padding 是卡片内留白；下面 bottomMargin 是卡片与下一张卡之间的外部距离。
        LinearLayout body = column(); body.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(10));
        card.addView(body, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = ui.dp(12);
        parent.addView(card, params);
        if (click != null) { card.setClickable(true); card.setFocusable(true); card.setOnClickListener(view -> click.run()); }
        return body;
    }
    /** 创建时间轴的一行，返回放事件卡片的区域；普通尺寸为左侧时间、右侧卡片。 */
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
        // 竖排中 height=0、weight=1，让线条占用时间文字与圆点之后的剩余高度。
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(ui.dp(1), 0, 1); lineParams.topMargin = ui.dp(5);
        axis.addView(line, lineParams);
        row.addView(axis, new LinearLayout.LayoutParams(ui.dp(56), -1));
        // 宽度 0 + weight=1 表示占满扣除左侧时间栏后的剩余宽度，而不是不可见。
        LinearLayout body = column(); row.addView(body, new LinearLayout.LayoutParams(0, -2, 1));
        ui.content.addView(row, new LinearLayout.LayoutParams(-1, -2)); return body;
    }
    /** 横向滚动容器只能有一个直接子 View，用一排 LinearLayout 承载多张酒店卡。 */
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
        // 这里只画可点击摘要；到底打开弹窗还是展开列表，由传入的 click 决定。
        LinearLayout body = card(ui.content, R.color.surface, click);
        text(body, title + "  ↗", 16, true);
        if (!subtitle.isEmpty()) text(body, subtitle, 12, false);
    }
}
