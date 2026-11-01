package com.example.odyssey;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.widget.NestedScrollView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 行程详情的底部弹层：按空行分成白色卡片，首行作标题，“标签: 值”显示成左右对齐的一行。
 * 内容都由 App 从结构化行程拼出来，这里只负责排版。
 */
final class JournalDetailSheet {
    private static final Pattern ROW = Pattern.compile("^([^:]{1,28}): (.+)$");

    static void show(Context context, String title, String body) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        NestedScrollView scroll = new NestedScrollView(context);
        LinearLayout content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL);
        HomeUi ui = new HomeUi(content); JournalUi journal = new JournalUi(ui);
        content.setPadding(ui.dp(16), ui.dp(20), ui.dp(16), ui.dp(16));
        content.setBackgroundColor(ui.color(R.color.background));
        scroll.addView(content);
        journal.heading(content, title, 24).setPadding(ui.dp(4), 0, ui.dp(4), ui.dp(8));
        for (String section : body.trim().split("\\n\\s*\\n")) {
            if (section.trim().isEmpty()) continue;
            LinearLayout card = ui.group();
            card.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(10));
            String[] lines = section.trim().split("\\n");
            for (int i = 0; i < lines.length; i++) {
                Matcher row = ROW.matcher(lines[i].trim());
                if (row.matches()) row(ui, journal, card, row.group(1), row.group(2));
                else journal.text(card, lines[i].trim(), i == 0 ? 16 : 14, i == 0);
            }
        }
        ui.button("Close", dialog::dismiss).setBackgroundTintList(android.content.res.ColorStateList.valueOf(ui.color(R.color.lime)));
        dialog.setContentView(scroll);
        dialog.setOnShowListener(ignored -> dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED));
        dialog.show();
    }
    private static void row(HomeUi ui, JournalUi journal, LinearLayout card, String label, String value) {
        LinearLayout row = journal.row(); row.setBaselineAligned(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = ui.dp(8);
        card.addView(row, params);
        TextView name = new TextView(ui.context); name.setText(label); name.setTextSize(14);
        AppFonts.style(name, android.graphics.Typeface.NORMAL); name.setTextColor(ui.color(R.color.text_secondary));
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 2f));
        TextView text = new TextView(ui.context); text.setText(value); text.setTextSize(14); text.setGravity(Gravity.END);
        AppFonts.style(text, android.graphics.Typeface.BOLD); text.setTextColor(ui.color(R.color.text_primary));
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 3f));
    }
    private JournalDetailSheet() { }
}
