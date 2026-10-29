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
            if (session.dateDraft.getStartDate() != 0) paragraph("Start: " + TripDates.format(session.dateDraft.getStartDate())
                    + (session.dateDraft.getEndDate() == 0 ? " · Choose your return date" : " → " + TripDates.format(session.dateDraft.getEndDate())));
            if (session.dateDraft.isStepComplete(2) && !session.dateDraft.isFlexibleDates()
                    && session.dateDraft.getEndDate() > session.dateDraft.getStartDate()) button("Confirm dates", actions::confirm, !loading);
        }
        for (PlanningSession.Option option : step.question.options) {
            MaterialCardView card = new MaterialCardView(content.getContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, dp(3), 0, dp(3)); card.setLayoutParams(params);
            card.setRadius(dp(20)); card.setCardElevation(0);
            boolean selected = step.selected.contains(option.id);
            // 选中只改变标题字重；背景/边框保持一致，selected 属性仍供读屏识别。
            card.setSelected(selected); card.setMinimumHeight(dp(48));
            card.setCardBackgroundColor(color(R.color.surface));
            card.setStrokeColor(color(R.color.outline)); card.setStrokeWidth(dp(1));
            LinearLayout body = new LinearLayout(content.getContext()); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(12), dp(8), dp(12), dp(8));
            if (session.current == 1) {
                ImageView image = new ImageView(content.getContext()); image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setContentDescription(option.title + " demo photo");
                body.addView(image, new LinearLayout.LayoutParams(-1, dp(72)));
                TextView credit = paragraphView("", 10); body.addView(credit);
                TripPhotos.showResponse(image, new ResponseImage(option.imageUrl, option.title + " atmosphere photo",
                        MockPhotoCatalog.url(option.value, true) != null && MockPhotoCatalog.url(option.value, true).equals(option.imageUrl)
                                ? "Photo: Unsplash · illustrative" : ""), option.value, credit);
            }
            TextView title = paragraphView(option.title, 17);
            title.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL); body.addView(title);
            body.addView(paragraphView(option.description, 13));
            if (session.current == 1) body.addView(paragraphView(String.join(" · ", option.keywords), 13));
            card.addView(body); card.setEnabled(!loading); card.setClickable(true);
            card.setOnClickListener(view -> actions.option(option.id)); content.addView(card);
        }
        if (session.current < 2) button(session.current == 0 ? "Confirm departure" : "Confirm destination",
                actions::confirm, !loading && !step.selected.isEmpty());
        if (session.current == 3 || session.current == 5) button("Continue", actions::confirm, !loading && !step.selected.isEmpty());
        if (loading) paragraph("Updating your local demo…");
    }
    private void button(String title, Runnable action, boolean enabled) {
        MaterialButton button = new MaterialButton(content.getContext()); button.setText(title); button.setEnabled(enabled);
        button.setBackgroundTintList(ColorStateList.valueOf(color(R.color.lime))); button.setTextColor(color(R.color.on_accent));
        button.setOnClickListener(view -> action.run()); content.addView(button);
    }
    private void paragraph(String value) { content.addView(paragraphView(value, 15)); }
    private TextView paragraphView(String value, int size) {
        TextView view = new TextView(content.getContext()); view.setText(value); view.setTextSize(size);
        view.setTypeface(null, Typeface.NORMAL);
        view.setTextColor(color(R.color.text_primary)); view.setPadding(0, dp(2), 0, dp(2)); return view;
    }
    private int color(int id) { return ContextCompat.getColor(content.getContext(), id); }
    private int dp(int value) { return Math.round(value * content.getResources().getDisplayMetrics().density); }
}
