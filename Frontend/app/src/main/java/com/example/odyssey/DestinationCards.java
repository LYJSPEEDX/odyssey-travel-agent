package com.example.odyssey;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.card.MaterialCardView;
import java.util.function.Consumer;

/** 目的地照片选择器；业务选择仍通过回调交给 TripPlanState，不把答案存在 View 里。 */
final class DestinationCards {
    static void add(LinearLayout parent, TripPlanState state, Consumer<String> choose) {
        HomeUi ui = new HomeUi(parent);
        // 小屏或大字体改成单列；避免三个关键词被挤成一串无法阅读的窄列。
        android.content.res.Configuration config = ui.context.getResources().getConfiguration();
        int columns = config.screenWidthDp < 360 || config.fontScale > 1.3f ? 1 : 2;
        LinearLayout row = null;
        int index = 0;
        for (TripOptions.Destination destination : TripOptions.DESTINATIONS) {
            if (destination.city.equalsIgnoreCase(state.getDeparture())) continue;
            if (index % columns == 0) {
                row = new LinearLayout(ui.context);
                row.setGravity(Gravity.TOP);
                parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            boolean selected = destination.city.equalsIgnoreCase(state.getDestination());
            MaterialCardView card = new MaterialCardView(ui.context);
            card.setTag("destination-" + destination.city);
            card.setRadius(ui.dp(20));
            card.setCardElevation(0);
            card.setCardBackgroundColor(ui.color(R.color.surface));
            card.setStrokeColor(ui.color(selected ? R.color.purple : R.color.outline));
            card.setStrokeWidth(ui.dp(selected ? 2 : 1));
            card.setSelected(selected); // 恢复/旋转时从 state 重画，而不是依赖卡片旧状态。
            card.setClickable(true);
            card.setFocusable(true);
            card.setContentDescription(ui.context.getString(R.string.editorial_select_destination,
                    destination.city, destination.description(), ui.context.getString(selected
                            ? R.string.editorial_selected : R.string.editorial_not_selected)));
            card.setOnClickListener(view -> choose.accept(destination.city));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            params.bottomMargin = ui.dp(12);
            if (index % columns == 0 && columns == 2) params.setMarginEnd(ui.dp(12));
            row.addView(card, params);
            LinearLayout body = new LinearLayout(ui.context);
            body.setOrientation(LinearLayout.VERTICAL);
            card.addView(body);
            FrameLayout photoFrame = new FrameLayout(ui.context);
            body.addView(photoFrame, new LinearLayout.LayoutParams(-1, ui.dp(columns == 1 ? 150 : 120)));
            ImageView image = new ImageView(ui.context);
            TripPhotos.show(image, destination.city);
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            photoFrame.addView(image, new FrameLayout.LayoutParams(-1, -1));
            if (selected) {
                TextView check = new TextView(ui.context);
                check.setText("✓");
                check.setTextSize(18);
                check.setTextColor(ui.color(R.color.on_accent));
                check.setGravity(Gravity.CENTER);
                android.graphics.drawable.GradientDrawable circle = new android.graphics.drawable.GradientDrawable();
                circle.setColor(ui.color(R.color.purple)); circle.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                circle.setStroke(ui.dp(2), ui.color(R.color.on_photo)); check.setBackground(circle);
                FrameLayout.LayoutParams mark = new FrameLayout.LayoutParams(ui.dp(30), ui.dp(30), Gravity.TOP | Gravity.END);
                mark.setMargins(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8));
                photoFrame.addView(check, mark); // 勾选符配合描边，不能只用颜色区分选择状态。
            }
            TextView title = new TextView(ui.context);
            title.setText(destination.city); title.setTextSize(18);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setTextColor(ui.color(R.color.text_primary));
            title.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(4));
            body.addView(title);
            TextView keywords = new TextView(ui.context);
            keywords.setText(destination.description()); keywords.setTextSize(12);
            keywords.setTextColor(ui.color(R.color.text_secondary));
            keywords.setPadding(ui.dp(12), 0, ui.dp(12), ui.dp(14));
            body.addView(keywords);
            index++;
        }
        // 排除出发地后可能剩单数张；补空位让最后一张仍保持半宽。
        if (columns == 2 && index % columns != 0 && row != null)
            row.addView(new View(ui.context), new LinearLayout.LayoutParams(0, 1, 1));
    }
    private DestinationCards() {}
}
