package com.example.odyssey;

import android.content.res.ColorStateList;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

/** 只负责画演示阶段；网络、事件和状态以后可独立替换为真实 SSE 数据源。 */
final class GenerationDemoRenderer {
    interface Actions {
        void toggleSummary();
        void toggleConnection();
        void finishDemo();
        void returnHome();
    }
    private final HomeUi ui;
    private final TripPlanState trip;
    private final Actions actions;
    GenerationDemoRenderer(LinearLayout content, TripPlanState trip, Actions actions) {
        ui = new HomeUi(content); this.trip = trip; this.actions = actions;
    }
    void render(int stage, boolean offline, boolean expanded) {
        ui.content.removeAllViews();
        ui.text("LIVE TRIP · DEMO", 11, true);
        ui.card(trip.getDeparture() + "  →  " + trip.getDestination(),
                trip.isFlexibleDates() ? "Flexible dates · Demo" : TripDates.format(trip.getStartDate()) + " – " + TripDates.format(trip.getEndDate()),
                R.color.lime, null);
        // 航点只是轻量动效，不表示真实进度；系统关闭动画时保持静态。
        ui.content.addView(new GenerationRouteView(ui.context), new LinearLayout.LayoutParams(-1, ui.dp(42)));
        ui.text("Usually takes a few minutes", 14, false);
        LinearProgressIndicator progress = new LinearProgressIndicator(ui.context);
        progress.setIndeterminate(true); progress.setIndicatorColor(ui.color(R.color.purple));
        progress.setTrackColor(ui.color(R.color.outline));
        LinearLayout.LayoutParams track = new LinearLayout.LayoutParams(-1, ui.dp(6));
        track.setMargins(0, ui.dp(4), 0, ui.dp(12)); ui.content.addView(progress, track);
        TextView status = ui.text(offline ? "Demo connection interrupted" : GenerationDemo.TITLES[stage] + "…", 19, true);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        ui.text(offline ? "This is a simulated disconnection. Reconnect to see the latest demo status."
                : "Simulated updates · No request has been sent.", 12, false);
        button(expanded ? "Hide trip details" : "View trip details", actions::toggleSummary);
        if (expanded) {
            ui.text("WITH  " + trip.getCompanion() + "\nBUDGET  " + trip.getBudget() + "\nPREFERENCES  "
                    + (trip.getConstraints().isEmpty() ? "None selected" : String.join(" · ", trip.getConstraints())), 14, false);
        }
        ui.text("Journey updates", 16, true);
        for (int i = 0; i <= stage; i++) {
            boolean current = i == stage;
            ui.card((current ? "NOW  ·  " : "DONE  ·  ") + GenerationDemo.TITLES[i],
                    GenerationDemo.DETAILS[i], R.color.surface, null);
        }
        button(offline ? "Reconnect demo" : "Simulate disconnect", actions::toggleConnection);
        button("Finish demo now", actions::finishDemo);
        button("Return to home", actions::returnHome);
        ui.text("The demo continues only while this app session is available. No real background job or SSE connection is running.", 12, false);
    }
    private void button(String text, Runnable click) {
        MaterialButton button = new MaterialButton(ui.context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(text); button.setAllCaps(false); button.setCornerRadius(ui.dp(20));
        button.setTextColor(ui.color(R.color.text_primary));
        button.setStrokeColor(ColorStateList.valueOf(ui.color(R.color.outline)));
        button.setMinHeight(ui.dp(48)); button.setOnClickListener(view -> click.run());
        ui.content.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }
}
