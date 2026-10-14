package com.example.odyssey;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
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
            text.setTypeface(null, Typeface.BOLD);
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
            card.setStrokeWidth(0);
        }
        card.setContentDescription(title + ". " + description);
        card.setClickable(click != null);
        if (click != null) card.setOnClickListener(view -> click.run());
        content.addView(card);
        return card;
    }
    MaterialButton button(int label, Runnable action) {
        MaterialButton button = new MaterialButton(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        button.setCornerRadius(dp(24));
        button.setBackgroundTintList(ColorStateList.valueOf(color(R.color.lime)));
        button.setTextColor(color(R.color.on_accent));
        button.setOnClickListener(view -> action.run());
        content.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }
    void section(int title) { text(context.getString(title), 18, true); }
    int color(int id) { return ContextCompat.getColor(context, id); }
    int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
