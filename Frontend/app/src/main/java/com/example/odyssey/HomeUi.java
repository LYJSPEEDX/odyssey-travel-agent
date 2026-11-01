package com.example.odyssey;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

/**
 * 首页、行程库和设置页共用的基础 View 工具；方法会创建控件并立即加入 content。
 * Java 的 LayoutParams 中 -1=match_parent、-2=wrap_content；正数尺寸需由 dp 转成 px。
 * 颜色参数传 R.color 资源 ID，再按当前浅色/深色主题解析，不直接传十六进制色值。
 */
final class HomeUi {
    final LinearLayout content;
    final Context context;
    HomeUi(LinearLayout content) { this.content = content; context = content.getContext(); }

    TextView text(String value, int size, boolean heading) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color(heading ? R.color.text_primary : R.color.text_secondary));
        text.setIncludeFontPadding(false);
        text.setLineSpacing(dp(4), 1);
        text.setPadding(0, dp(8), 0, dp(12));
        if (heading) {
            // 20sp 及以上才算页面标题，小号的加粗文字仍用正文字体。
            if (size >= 20) AppFonts.heading(text); else AppFonts.style(text, Typeface.BOLD);
            ViewCompat.setAccessibilityHeading(text, true);
        }
        content.addView(text);
        return text;
    }
    void title(int title, int subtitle) {
        text(context.getString(title), 28, true);
        text(context.getString(subtitle), 14, false);
    }
    MaterialCardView card(String title, String description, int background, Runnable click) {
        // click 为 null 表示只读展示卡；非空时将整张卡作为可点击区域。
        MaterialCardView card = (MaterialCardView) LayoutInflater.from(context)
                .inflate(R.layout.item_trip_option, content, false);
        TextView name = card.findViewById(R.id.optionTitle);
        TextView body = card.findViewById(R.id.optionDescription);
        name.setText(title);
        name.setTextSize(18);
        body.setText(description);
        body.setLineSpacing(dp(4), 1);
        card.setCardBackgroundColor(color(background));
        if (background != R.color.surface) {
            name.setTextColor(color(R.color.on_accent));
            body.setTextColor(color(R.color.on_accent));
        }
        // 卡片不描边，靠页面底色与卡片底色的差别分层。
        card.setStrokeWidth(0);
        card.setContentDescription(title + ". " + description);
        card.setClickable(click != null);
        if (click != null) card.setOnClickListener(view -> click.run());
        content.addView(card);
        return card;
    }
    MaterialButton button(int label, Runnable action) { return button(context.getString(label), action); }
    MaterialButton button(String label, Runnable action) {
        MaterialButton button = new MaterialButton(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        button.setCornerRadius(dp(24));
        button.setBackgroundTintList(ColorStateList.valueOf(color(R.color.lime)));
        button.setTextColor(color(R.color.on_accent));
        AppFonts.style(button, Typeface.BOLD);
        button.setOnClickListener(view -> action.run());
        content.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }
    /** 白色合并卡片：同组内容放在一张无描边圆角卡片上，页面底色作为分隔。 */
    LinearLayout group() {
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackgroundResource(R.drawable.bg_group);
        group.setClipToOutline(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(16);
        content.addView(group, params);
        return group;
    }
    /** 合并卡片中的一行，只放左侧标题；右侧内容由调用方追加。 */
    LinearLayout row(String label) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        row.setPadding(dp(16), 0, dp(4), 0);
        row.setBackgroundResource(attr(android.R.attr.selectableItemBackground));
        TextView name = new TextView(context);
        name.setText(label);
        name.setTextSize(15);
        name.setTextColor(color(R.color.text_secondary));
        row.addView(name);
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }
    /** 设置类的操作行：左侧标题、右侧当前值；click 为 null 时只读展示。 */
    LinearLayout actionRow(String label, String value, Runnable click) {
        LinearLayout row = row(label);
        ((TextView) row.getChildAt(0)).setTextColor(color(R.color.text_primary));
        TextView current = new TextView(context);
        current.setText(value);
        current.setTextSize(14);
        current.setTextColor(color(R.color.text_secondary));
        current.setGravity(android.view.Gravity.END);
        current.setSingleLine(true);
        current.setEllipsize(android.text.TextUtils.TruncateAt.END);
        current.setPadding(dp(12), 0, click == null ? dp(12) : 0, 0);
        row.addView(current, new LinearLayout.LayoutParams(0, -2, 1f));
        if (click == null) row.setBackground(null);
        else {
            rowIcon(row, R.drawable.ic_chevron_right);
            row.setOnClickListener(view -> click.run());
        }
        return row;
    }
    /** 行尾的装饰图标，不拦截点击，由整行响应。 */
    ImageView rowIcon(LinearLayout row, int icon) {
        ImageView image = new ImageView(context);
        image.setImageResource(icon);
        image.setImageTintList(ColorStateList.valueOf(color(R.color.text_secondary)));
        image.setPadding(dp(14), dp(14), dp(14), dp(14));
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(image, new LinearLayout.LayoutParams(dp(48), dp(48)));
        return image;
    }
    int attr(int id) {
        android.util.TypedValue value = new android.util.TypedValue();
        context.getTheme().resolveAttribute(id, value, true);
        return value.resourceId;
    }
    void section(int title) { text(context.getString(title), 18, true); }
    int color(int id) { return ContextCompat.getColor(context, id); }
    int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
