package com.example.odyssey;

import android.graphics.Typeface;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

/** 统一字体入口：setTypeface(null, style) 会退回系统字体，代码里改字重都走这里。 */
final class AppFonts {
    private AppFonts() { }

    static void style(TextView view, int style) {
        view.setTypeface(Typeface.create(ResourcesCompat.getFont(view.getContext(), R.font.plus_jakarta_sans), style));
    }

    static void medium(TextView view) {
        view.setTypeface(ResourcesCompat.getFont(view.getContext(), R.font.plus_jakarta_sans_medium));
    }

    static void heading(TextView view) { heading(view, Typeface.NORMAL); }

    static void heading(TextView view, int style) {
        view.setTypeface(Typeface.create(ResourcesCompat.getFont(view.getContext(), R.font.fraunces), style));
    }
}
