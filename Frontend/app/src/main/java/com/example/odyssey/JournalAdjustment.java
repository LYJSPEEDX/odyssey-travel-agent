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

/**
 * AI 调整入口的离线预览：快捷按钮只填入文字，不调用模型，也不修改当前行程。
 * 草稿由外部 Activity 持有；本类通过 changed 回调传出文字，不直接负责存储。
 */
final class JournalAdjustment {
    private BottomSheetDialog dialog;
    void show(Context context, String draft, Consumer<String> changed) {
        // Consumer<String> 是接收文字、不返回结果的函数；changed.accept(text) 通知 Activity。
        // 防止用户连续点击入口时叠加多个弹层。
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
        journal.text(content, "AI adjustments are not connected yet. Your draft stays on this screen only and is cleared when you leave the trip. Your itinerary will not change.", 14, false);
        ChipGroup options = new ChipGroup(context); options.setChipSpacingHorizontal(ui.dp(8));
        content.addView(options);
        TextInputLayout field = new TextInputLayout(context);
        field.setHint("What would you change?"); field.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        // Layout 提供标签与描边，EditText 才是真正接收文字输入的控件。
        TextInputEditText input = new TextInputEditText(field.getContext()); input.setTag("adjustment-input");
        // | 合并三种输入标记：文本、多行、句首大写提示，不表示三个输入框。
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMinLines(3); input.setMaxLines(6); input.setGravity(Gravity.TOP | Gravity.START);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});
        input.setText(draft); field.addView(input);
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(-1, -2);
        fieldParams.topMargin = ui.dp(12); fieldParams.bottomMargin = ui.dp(12); content.addView(field, fieldParams);
        // 两个数组按相同下标对应：labels 是短标题，requests 是点击后填入的完整句子。
        String[] labels = {"Less walking", "Lower budget", "More free time"};
        String[] requests = {"Please reduce walking and allow more breaks.",
                "Please suggest a lower-cost version of this trip.", "Please leave more free time between activities."};
        for (int i = 0; i < labels.length; i++) {
            String request = requests[i]; Chip chip = new Chip(context);
            chip.setText(labels[i]); chip.setEnsureMinTouchTargetSize(true); chip.setCheckable(false);
            chip.setChipBackgroundColor(ColorStateList.valueOf(ui.color(R.color.surface)));
            chip.setTextColor(ui.color(R.color.text_primary));
            // 点击替换当前草稿并将光标放到末尾，不直接“发送请求”。
            chip.setOnClickListener(view -> { input.setText(request); input.setSelection(request.length()); });
            options.addView(chip);
        }
        MaterialButton keep = ui.button(R.string.journal_keep_draft, () -> {
            changed.accept(input.getText() == null ? "" : input.getText().toString()); dialog.dismiss();
        });
        // 只有非空白文字才允许保留；下面输入监听器会持续更新这个状态。
        keep.setEnabled(!InputValidation.text(draft).isEmpty());
        // 每次编辑都通知 Activity，关闭弹层或页面重建时也能恢复，而非只有 Keep draft 才记录。
        input.addTextChangedListener(new TextWatcher() {
            // 接口要求三个方法；本功能只用 onTextChanged，其余两个方法保持空实现。
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                // s 是变化后的完整文字；accept 同步草稿，再更新 Keep draft 是否可点。
                changed.accept(s.toString()); keep.setEnabled(!InputValidation.text(s.toString()).isEmpty());
            }
            @Override public void afterTextChanged(Editable value) {}
        });
        // 组装内容交给弹层；创建控件不等于已显示，最后 dialog.show 才让用户看见。
        dialog.setContentView(scroll);
        // 展开行为要等弹层显示后设置，所以放在 onShow 回调，而不是提前直接调用。
        dialog.setOnShowListener(ignored -> dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED));
        dialog.show();
    }
    void dismiss() { if (dialog != null) dialog.dismiss(); }
}
