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
import java.util.List;

/** Renders the current form only; TripPlanState owns all answers and validation. */
final class PlannerFormRenderer {
    interface Actions {
        void changed();
        void chooseDeparture(String city);
        void editLocation(boolean departure);
        void dateSelected(int date);
        void back();
        int saveStatus();
        void saveTrip();
        void viewSavedTrips();
        void viewItinerary();
    }

    private final Context context;
    private final LinearLayout content;
    private final TripPlanState state;
    private final Calendar month;
    private final Actions actions;

    PlannerFormRenderer(LinearLayout content, TripPlanState state, Calendar month, Actions actions) {
        this.context = content.getContext();
        this.content = content;
        this.state = state;
        this.month = month;
        this.actions = actions;
    }

    void render() {
        content.removeAllViews();
        if (state.isGenerating() || state.isGenerated()) { generation(); return; }
        switch (state.getCurrentStep()) {
            case 1: location(); break;
            case 2: dates(); break;
            case 3: companions(); break;
            case 4: budgets(); break;
            case 5: constraints(); break;
            default: break;
        }
    }

    private void location() {
        label(R.string.departure_section);
        if (state.getDeparture().isEmpty()) {
            ChipGroup origins = new ChipGroup(context);
            origins.setChipSpacingHorizontal(dp(8));
            for (String city : TripOptions.DEPARTURES) {
                Chip chip = new Chip(context);
                chip.setText(city);
                chip.setEnsureMinTouchTargetSize(true);
                chip.setChipBackgroundColor(ColorStateList.valueOf(color(R.color.surface)));
                chip.setTextColor(color(R.color.text_primary));
                chip.setOnClickListener(view -> actions.chooseDeparture(city));
                origins.addView(chip);
            }
            content.addView(origins);
            paragraph(context.getString(R.string.choose_departure_first));
            return;
        }
        option(state.getDeparture(), context.getString(R.string.change_departure), true,
                () -> actions.editLocation(true));
        label(R.string.destination_section);
        for (TripOptions.Destination destination : TripOptions.DESTINATIONS) {
            if (destination.city.equalsIgnoreCase(state.getDeparture())) continue;
            option(destination.city, destination.description(),
                    destination.city.equalsIgnoreCase(state.getDestination()), () -> {
                        state.selectDestination(destination.city);
                        actions.changed();
                    });
        }
        if (!state.getDestination().isEmpty() && TripOptions.destination(state.getDestination()) == null) {
            option(state.getDestination(), context.getString(R.string.custom_city_description), true,
                    () -> actions.editLocation(false));
        }
        button(R.string.change_destination, () -> actions.editLocation(false));
    }

    private void dates() {
        label(R.string.date_section);
        String status = state.isFlexibleDates() ? context.getString(R.string.flexible_dates_summary)
                : state.getStartDate() == 0 ? context.getString(R.string.date_start_prompt)
                : state.getEndDate() == 0 ? context.getString(R.string.date_end_prompt,
                        TripDates.format(state.getStartDate()))
                : context.getResources().getQuantityString(R.plurals.date_range_complete,
                        state.getDurationDays(), dateSummary(), state.getDurationDays());
        paragraph(status);
        if (!state.isFlexibleDates() && state.getEndDate() != 0 && state.getDurationDays() > 60)
            paragraph(context.getString(R.string.itinerary_duration_limit));
        content.addView(new TripCalendarView(context, state, month, actions::dateSelected, () -> {}));
        paragraph(context.getString(R.string.calendar_legend));
        option(context.getString(R.string.flexible_dates),
                context.getString(R.string.flexible_dates_description), state.isFlexibleDates(), () -> {
                    state.useFlexibleDates();
                    actions.changed();
                });
        savedNote(2);
    }

    private void companions() {
        label(R.string.companion_section);
        boolean known = false;
        for (TripOptions.Option option : TripOptions.COMPANIONS) {
            boolean selected = option.title.equals(state.getCompanion());
            known |= selected;
            option(option.title, option.description, selected, () -> {
                state.setCompanion(option.title);
                actions.changed();
            });
        }
        if (!known && !state.getCompanion().isEmpty()) {
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

    private void constraints() {
        label(R.string.constraints_section);
        for (TripOptions.Option option : TripOptions.CONSTRAINTS) {
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
            // Use the same submit path as the top navigation, including unsent text.
            View next = content.getRootView().findViewById(R.id.nextButton);
            next.performClick();
        });
    }

    private void generation() {
        if (state.isGenerating()) {
            LinearProgressIndicator progress = new LinearProgressIndicator(context);
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
            TextView saved = paragraph(context.getString(actions.saveStatus() == 1
                    ? R.string.trip_saved : actions.saveStatus() == 2
                    ? R.string.trip_save_failed : R.string.trip_saving));
            saved.setId(R.id.saveTripStatus);
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
        MaterialCardView card = (MaterialCardView) LayoutInflater.from(context)
                .inflate(R.layout.item_trip_option, content, false);
        TextView heading = card.findViewById(R.id.optionTitle);
        TextView body = card.findViewById(R.id.optionDescription);
        heading.setText(selected ? context.getString(R.string.selected_option, title) : title);
        body.setText(description);
        card.setCardBackgroundColor(color(selected ? R.color.lime : R.color.surface));
        heading.setTextColor(color(selected ? R.color.on_accent : R.color.text_primary));
        body.setTextColor(color(selected ? R.color.on_accent : R.color.text_secondary));
        card.setStrokeColor(color(selected ? R.color.on_accent : R.color.outline));
        card.setSelected(selected);
        card.setContentDescription(context.getString(selected ? R.string.selected_accessibility
                : R.string.option_accessibility, title, description));
        card.setClickable(action != null);
        card.setFocusable(true);
        if (action != null) card.setOnClickListener(view -> action.run());
        content.addView(card);
    }
    private void label(int string) {
        TextView label = paragraph(context.getString(string));
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        ViewCompat.setAccessibilityHeading(label, true);
    }
    private TextView paragraph(String text) {
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
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        button.setCornerRadius(dp(20));
        button.setBackgroundTintList(ColorStateList.valueOf(color(R.color.mint)));
        button.setTextColor(color(R.color.on_accent));
        button.setOnClickListener(view -> action.run());
        content.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }
    private int color(int id) { return ContextCompat.getColor(context, id); }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
