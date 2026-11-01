package com.example.odyssey;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import java.util.Calendar;

/** 动态表单只展示 session；所有提交由 Activity 送过同一 Mock JSON 边界。 */
final class PlanningSessionRenderer {
    interface Actions {
        void option(String id);
        void confirm();
        void date(int value);
        void redraw();
        void useChatAnswer();
    }
    private final LinearLayout content;
    private final Actions actions;
    PlanningSessionRenderer(LinearLayout content, Actions actions) { this.content = content; this.actions = actions; }
    void render(PlanningSession session, Calendar month, boolean loading) {
        content.removeAllViews();
        PlanningSession.Step step = session.step();
        if (step.state.equals("NEEDS_REVIEW")) paragraph("Needs review · Previous answer: " + step.answer);
        else if (!step.answer.isEmpty()) paragraph((step.state.equals("CONFIRMED") ? "Confirmed: " : "Previous answer: ") + step.answer);
        if (session.current == 2) {
            content.addView(new TripCalendarView(content.getContext(), session.dateDraft, month, actions::date, actions::redraw));
            if (session.dateDraft.isStepComplete(2) && !session.dateDraft.isFlexibleDates()) button("Confirm dates", actions::confirm, !loading);
        }
        // 第 5 步选项多，两列排列；同一行的卡片等高。
        boolean twoColumns = session.current == 5;
        LinearLayout gridRow = null;
        for (int index = 0; index < step.question.options.size(); index++) {
            PlanningSession.Option option = step.question.options.get(index);
            MaterialCardView card = new MaterialCardView(content.getContext());
            LinearLayout.LayoutParams params = twoColumns ? new LinearLayout.LayoutParams(0, -1, 1f) : new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, dp(6), 0, dp(6)); card.setLayoutParams(params);
            if (twoColumns && index % 2 == 1) params.setMarginStart(dp(10));
            card.setRadius(dp(20)); card.setCardElevation(0);
            boolean selected = step.selected.contains(option.id);
            // 未选中无边框；选中时紫色填充加边框，selected 属性仍供读屏识别。日期页的灵活选项用红橙色与日历区分。
            card.setSelected(selected); card.setMinimumHeight(dp(48));
            card.setCardBackgroundColor(color(selected ? R.color.purple : session.current == 2 ? R.color.red : R.color.surface));
            card.setStrokeColor(color(R.color.text_primary)); card.setStrokeWidth(selected ? dp(1) : 0);
            LinearLayout body = new LinearLayout(content.getContext()); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(12), dp(8), dp(12), dp(8));
            if (session.current == 1) {
                // 图片四周留足间隙，圆角比外层卡片小一档，看起来才是同心的。
                body.setPadding(dp(14), dp(14), dp(14), dp(12));
                com.google.android.material.imageview.ShapeableImageView image = new com.google.android.material.imageview.ShapeableImageView(content.getContext());
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setShapeAppearanceModel(com.google.android.material.shape.ShapeAppearanceModel.builder().setAllCornerSizes(dp(12)).build());
                image.setContentDescription(option.title + " demo photo");
                LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(-1, dp(72)); imageParams.bottomMargin = dp(8);
                body.addView(image, imageParams);
                TripPhotos.showResponse(image, new ResponseImage(option.imageUrl, option.title + " atmosphere photo", ""), option.value);
            }
            TextView title = paragraphView(option.title, 17);
            AppFonts.style(title, selected || session.current == 1 || session.current == 2 ? Typeface.BOLD : Typeface.NORMAL);
            // 预算把描述开头的价格放到标题行右上角；其他步骤标题独占一行。
            int priceAt = session.current == 4 ? option.description.indexOf("AUD ") : -1;
            int priceEnd = priceAt < 0 ? -1 : option.description.indexOf(" · ", priceAt);
            String caption = option.description;
            if (priceEnd > 0) {
                LinearLayout row = new LinearLayout(content.getContext()); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
                TextView price = paragraphView(option.description.substring(priceAt, priceEnd), 14);
                AppFonts.style(price, Typeface.BOLD); row.addView(price);
                body.addView(row);
                caption = option.description.substring(priceEnd + 3);
            } else body.addView(title);
            if (!caption.isEmpty()) {
                TextView description = paragraphView(caption, 13);
                if (session.current == 1) AppFonts.style(description, Typeface.BOLD);
                body.addView(description);
            }
            if (session.current == 1) body.addView(paragraphView(String.join(" · ", option.keywords), 13));
            if (selected || session.current == 2) tintText(body, color(R.color.on_accent));
            card.addView(body); card.setEnabled(!loading); card.setClickable(true);
            card.setOnClickListener(view -> actions.option(option.id));
            if (!twoColumns) { content.addView(card); continue; }
            if (index % 2 == 0) {
                gridRow = new LinearLayout(content.getContext()); gridRow.setBaselineAligned(false);
                content.addView(gridRow, new LinearLayout.LayoutParams(-1, -2));
            }
            gridRow.addView(card);
            // 奇数个选项时补一个空位，最后一张卡片不会撑满整行。
            if (index % 2 == 0 && index == step.question.options.size() - 1)
                gridRow.addView(new android.view.View(content.getContext()), new LinearLayout.LayoutParams(0, 0, 1f));
        }
        if (session.current < 2) button(session.current == 0 ? "Confirm departure" : "Confirm destination",
                actions::confirm, !loading && !step.selected.isEmpty());
        if (session.current == 3 || session.current == 5) button("Continue", actions::confirm, !loading && !step.selected.isEmpty());
        for (PlanningSession.Message message : step.messages) {
            TextView bubble = paragraphView((message.role.equals("USER") ? "You · " : "AI demo · ") + message.text, 14);
            bubble.setPadding(dp(12), dp(10), dp(12), dp(10));
            if (message.role.equals("ASSISTANT")) bubble.setTextColor(color(R.color.on_accent));
            bubble.setBackgroundResource(message.role.equals("USER") ? R.drawable.bg_user_chat : R.drawable.bg_chat_bubble);
            LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(-1, -2); row.setMargins(0, dp(4), 0, dp(4));
            content.addView(bubble, row);
        }
        if (session.current >= 3 && !step.chatCandidate.isEmpty())
            button("Use as answer", actions::useChatAnswer, !loading);
        if (loading) paragraph("AI demo is replying…");
    }
    private void button(String title, Runnable action, boolean enabled) {
        MaterialButton button = new MaterialButton(content.getContext()); button.setText(title); button.setEnabled(enabled);
        button.setBackgroundTintList(ColorStateList.valueOf(color(R.color.lime))); button.setTextColor(color(R.color.on_accent)); AppFonts.style(button, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(6);
        button.setOnClickListener(view -> action.run()); content.addView(button, params);
    }
    private void tintText(android.view.ViewGroup group, int value) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView) ((TextView) child).setTextColor(value);
            else if (child instanceof android.view.ViewGroup) tintText((android.view.ViewGroup) child, value);
        }
    }
    private void paragraph(String value) { content.addView(paragraphView(value, 15)); }
    private TextView paragraphView(String value, int size) {
        TextView view = new TextView(content.getContext()); view.setText(value); view.setTextSize(size);
        AppFonts.style(view, Typeface.NORMAL);
        view.setTextColor(color(R.color.text_primary)); view.setPadding(0, dp(2), 0, dp(2)); return view;
    }
    private int color(int id) { return ContextCompat.getColor(content.getContext(), id); }
    private int dp(int value) { return Math.round(value * content.getResources().getDisplayMetrics().density); }
}
