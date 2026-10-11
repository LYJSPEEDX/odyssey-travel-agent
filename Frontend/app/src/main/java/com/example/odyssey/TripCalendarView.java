package com.example.odyssey;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.function.IntConsumer;

/** Inline date-range calendar. Month navigation never changes the selected range. */
@SuppressLint("ViewConstructor") // Runtime-only view: state and selection callback are required.
final class TripCalendarView extends LinearLayout {
    private final TripPlanState state;
    private final Calendar month;
    private final IntConsumer onDateSelected;
    private final Runnable onMonthChanged;

    TripCalendarView(Context context, TripPlanState state, Calendar month,
                     IntConsumer onDateSelected, Runnable onMonthChanged) {
        super(context);
        this.state = state;
        this.month = month;
        this.onDateSelected = onDateSelected;
        this.onMonthChanged = onMonthChanged;
        setOrientation(VERTICAL);
        setPadding(dp(8), dp(8), dp(8), dp(8));
        setBackground(shape(R.color.surface, 20));
        drawMonth();
    }

    private void drawMonth() {
        removeAllViews();
        LinearLayout header = row();
        MaterialButton previous = monthButton(R.drawable.ic_arrow_back, R.string.previous_month, -1);
        Calendar today = Calendar.getInstance();
        previous.setEnabled(month.get(Calendar.YEAR) > today.get(Calendar.YEAR)
                || month.get(Calendar.MONTH) > today.get(Calendar.MONTH));
        header.addView(previous, new LayoutParams(dp(48), dp(48)));
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH);
        monthFormat.setTimeZone(month.getTimeZone());
        TextView label = text(monthFormat.format(month.getTime()));
        label.setTextSize(18);
        label.setTypeface(null, Typeface.BOLD);
        header.addView(label, new LayoutParams(0, dp(48), 1));
        header.addView(monthButton(R.drawable.ic_arrow_forward, R.string.next_month, 1),
                new LayoutParams(dp(48), dp(48)));
        addView(header);
        LinearLayout weekdays = row();
        for (String day : getResources().getStringArray(R.array.calendar_weekdays)) {
            TextView name = text(day);
            name.setTextColor(color(R.color.text_secondary));
            weekdays.addView(name, new LayoutParams(0, dp(32), 1));
        }
        addView(weekdays);
        Calendar first = (Calendar) month.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        int offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int count = first.getActualMaximum(Calendar.DAY_OF_MONTH);
        int todayKey = TripDates.key(today);
        LinearLayout week = null;
        for (int cell = 0; cell < ((offset + count + 6) / 7) * 7; cell++) {
            if (cell % 7 == 0) { week = row(); addView(week); }
            int day = cell - offset + 1;
            TextView date = text(day > 0 && day <= count ? String.valueOf(day) : "");
            LayoutParams params = new LayoutParams(0, dp(48), 1);
            week.addView(date, params);
            if (day < 1 || day > count) continue;
            first.set(Calendar.DAY_OF_MONTH, day);
            int key = TripDates.key(first);
            date.setTag("date-" + key);
            date.setContentDescription(TripDates.format(key));
            boolean endpoint = key == state.getStartDate() || key == state.getEndDate();
            boolean inRange = state.getEndDate() != 0 && key > state.getStartDate()
                    && key < state.getEndDate();
            if (endpoint || inRange) {
                date.setBackground(shape(endpoint ? R.color.lime : R.color.mint, endpoint ? 24 : 4));
                date.setTextColor(color(R.color.on_accent));
                date.setTypeface(null, endpoint ? Typeface.BOLD : Typeface.NORMAL);
                date.setSelected(true);
                int desc = key == state.getStartDate() ? R.string.calendar_departure
                        : key == state.getEndDate() ? R.string.calendar_return : R.string.calendar_selected_day;
                date.setContentDescription(getResources().getString(desc, TripDates.format(key)));
            }
            date.setEnabled(key >= todayKey);
            if (key < todayKey) date.setAlpha(0.35f);
            else {
                date.setFocusable(true);
                date.setOnClickListener(view -> onDateSelected.accept(key));
            }
        }
    }

    private MaterialButton monthButton(int icon, int description, int direction) {
        MaterialButton button = new MaterialButton(getContext());
        button.setIconResource(icon);
        button.setIconTint(ColorStateList.valueOf(color(R.color.text_primary)));
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setIconPadding(0);
        button.setIconSize(dp(20));
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setBackgroundTintList(ColorStateList.valueOf(color(R.color.surface)));
        button.setContentDescription(getResources().getString(description));
        button.setOnClickListener(view -> {
            month.add(Calendar.MONTH, direction);
            drawMonth();
            onMonthChanged.run();
        });
        return button;
    }
    private LinearLayout row() {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }
    private TextView text(String text) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(14);
        view.setTextColor(color(R.color.text_primary));
        view.setIncludeFontPadding(false);
        return view;
    }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(color));
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }
    private int color(int id) { return ContextCompat.getColor(getContext(), id); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
