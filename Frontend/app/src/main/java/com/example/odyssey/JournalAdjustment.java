package com.example.odyssey;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.LinearLayout;
import androidx.core.widget.NestedScrollView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.function.Consumer;

/** Collects a local draft only. Never changes or sends the itinerary. */
final class JournalAdjustment {
    private BottomSheetDialog dialog;
    void show(Context context, String draft, Consumer<String> changed) {
        if (dialog != null && dialog.isShowing()) return;
        dialog = new BottomSheetDialog(context);
        NestedScrollView scroll = new NestedScrollView(context);
        LinearLayout content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL);
        HomeUi ui = new HomeUi(content); JournalUi journal = new JournalUi(ui);
        content.setPadding(ui.dp(24), ui.dp(20), ui.dp(24), ui.dp(24));
        content.setBackgroundColor(ui.color(R.color.background));
        scroll.addView(content);
        journal.tag(content, "Offline preview · Not sent", R.color.mint);
        journal.heading(content, "Make it your kind of trip", 24).setPadding(0, ui.dp(16), 0, 0);
        journal.text(content, "AI is not connected yet. Your draft stays on this screen only and is cleared when you leave the trip. Your itinerary will not change.", 14, false);
        ChipGroup options = new ChipGroup(context); options.setChipSpacingHorizontal(ui.dp(8));
        content.addView(options);
        TextInputLayout field = new TextInputLayout(context);
        field.setHint("What would you change?"); field.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText input = new TextInputEditText(field.getContext()); input.setTag("adjustment-input");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMinLines(3); input.setMaxLines(6); input.setGravity(Gravity.TOP | Gravity.START);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});
        input.setText(draft); field.addView(input);
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(-1, -2);
        fieldParams.topMargin = ui.dp(12); fieldParams.bottomMargin = ui.dp(12); content.addView(field, fieldParams);
        String[] labels = {"Less walking", "Lower budget", "More free time"};
        String[] requests = {"Please reduce walking and allow more breaks.",
                "Please suggest a lower-cost version of this trip.", "Please leave more free time between activities."};
        for (int i = 0; i < labels.length; i++) {
            String request = requests[i]; Chip chip = new Chip(context);
            chip.setText(labels[i]); chip.setEnsureMinTouchTargetSize(true); chip.setCheckable(false);
            chip.setChipBackgroundColor(ColorStateList.valueOf(ui.color(R.color.surface)));
            chip.setTextColor(ui.color(R.color.text_primary));
            chip.setOnClickListener(view -> { input.setText(request); input.setSelection(request.length()); });
            options.addView(chip);
        }
        MaterialButton keep = ui.button(R.string.journal_keep_draft, () -> {
            changed.accept(input.getText() == null ? "" : input.getText().toString()); dialog.dismiss();
        });
        keep.setEnabled(!draft.trim().isEmpty());
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                changed.accept(s.toString()); keep.setEnabled(!s.toString().trim().isEmpty());
            }
            @Override public void afterTextChanged(Editable value) {}
        });
        dialog.setContentView(scroll);
        dialog.setOnShowListener(ignored -> dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED));
        dialog.show();
    }
    void dismiss() { if (dialog != null) dialog.dismiss(); }
}
