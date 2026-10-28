package com.example.odyssey;

import android.content.Context;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** 年、月、日下拉选择，不弹键盘；合法日期统一交给 InputValidation 校验。 */
final class BirthDatePicker {
    interface Result { void selected(String date); }
    static void show(Context context, String initial, Result result) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_Odyssey_ProfilePicker);
        Context themed = builder.getContext();
        int padding = Math.round(24 * context.getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(themed);
        content.setOrientation(LinearLayout.VERTICAL); content.setPadding(padding, 0, padding, 0);
        List<String> years = new ArrayList<>(); years.add("Year");
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        for (int year = currentYear; year >= currentYear - 120; year--) years.add(Integer.toString(year));
        Spinner year = dropdown(themed, content, "Year", years);
        List<String> months = new ArrayList<>(); months.add("Month");
        String[] names = new java.text.DateFormatSymbols(Locale.ENGLISH).getMonths();
        for (int month = 0; month < 12; month++) months.add(names[month]);
        Spinner month = dropdown(themed, content, "Month", months);
        Spinner day = dropdown(themed, content, "Day", days(31));
        TextView error = new TextView(themed); error.setTextColor(context.getColor(R.color.launch_coral));
        error.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); content.addView(error);
        // 选定年月后重建天数；例如二月不提供 30、31 日。原日期不再有效时回到 Day 占位项。
        Runnable updateDays = () -> {
            int selected = day.getSelectedItemPosition();
            Calendar calendar = Calendar.getInstance(); calendar.clear();
            calendar.set(year.getSelectedItemPosition() == 0 ? 2000 : Integer.parseInt((String) year.getSelectedItem()),
                    Math.max(0, month.getSelectedItemPosition() - 1), 1);
            int count = month.getSelectedItemPosition() == 0 ? 31 : calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
            day.setAdapter(adapter(themed, days(count)));
            day.setSelection(selected <= count ? selected : 0);
        };
        android.widget.AdapterView.OnItemSelectedListener listener = new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { updateDays.run(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        };
        year.setOnItemSelectedListener(listener); month.setOnItemSelectedListener(listener);
        if (initial.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            int index = years.indexOf(initial.substring(0, 4));
            if (index > 0) {
                year.setSelection(index); month.setSelection(Integer.parseInt(initial.substring(5, 7)));
                updateDays.run(); day.setSelection(Integer.parseInt(initial.substring(8, 10)));
            }
        }
        androidx.appcompat.app.AlertDialog dialog = builder.setTitle("Date of birth").setView(content)
                .setPositiveButton("Done", null)
                .setNeutralButton("Prefer not to tell", (which, button) -> result.selected(""))
                .setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (year.getSelectedItemPosition() == 0 || month.getSelectedItemPosition() == 0 || day.getSelectedItemPosition() == 0) {
                error.setText("Select year, month and day, or choose Prefer not to tell."); return;
            }
            String date = String.format(Locale.US, "%s-%02d-%02d", year.getSelectedItem(), month.getSelectedItemPosition(), day.getSelectedItemPosition());
            try { result.selected(InputValidation.birthDate(date)); dialog.dismiss(); }
            catch (IllegalArgumentException invalid) { error.setText(invalid.getMessage()); }
        }));
        dialog.show();
    }
    private static Spinner dropdown(Context context, LinearLayout content, String label, List<String> options) {
        Spinner spinner = new Spinner(context, Spinner.MODE_DROPDOWN);
        spinner.setContentDescription(label); spinner.setAdapter(adapter(context, options));
        content.addView(spinner, new LinearLayout.LayoutParams(-1, Math.round(56 * context.getResources().getDisplayMetrics().density)));
        return spinner;
    }
    private static ArrayAdapter<String> adapter(Context context, List<String> items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); return adapter;
    }
    private static List<String> days(int count) {
        List<String> result = new ArrayList<>(); result.add("Day");
        for (int day = 1; day <= count; day++) result.add(Integer.toString(day));
        return result;
    }
    private BirthDatePicker() { }
}
