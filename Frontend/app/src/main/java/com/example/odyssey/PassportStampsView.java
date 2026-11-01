package com.example.odyssey;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/**
 * 护照邮戳装饰，贴右下角绘制：不响应点击，也不进入读屏顺序。
 * 放在弹性留白里时，空间不够就少画或不画，不会挤到内容。
 */
public final class PassportStampsView extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp = getResources().getDisplayMetrics().density;

    public PassportStampsView(Context context) { this(context, null); }
    public PassportStampsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        android.content.res.TypedArray values = context.obtainStyledAttributes(attrs, R.styleable.PassportStampsView);
        int ink = values.getColor(R.styleable.PassportStampsView_stampColor, context.getColor(R.color.stamp_ink));
        values.recycle();
        line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(1.5f * dp); line.setColor(ink);
        label.setColor(ink); label.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        label.setTextAlign(Paint.Align.CENTER);
    }
    @Override protected void onDraw(Canvas canvas) {
        float right = getWidth(), bottom = getHeight();
        if (bottom < 120 * dp) return;
        round(canvas, right - 62 * dp, bottom - 64 * dp, -14);
        box(canvas, right - 150 * dp, bottom - 42 * dp, 7);
        if (bottom >= 190 * dp) oval(canvas, right - 44 * dp, bottom - 158 * dp, 18);
    }
    private void round(Canvas canvas, float x, float y, float angle) {
        canvas.save(); canvas.rotate(angle, x, y);
        float outer = 50 * dp, inner = 33 * dp;
        canvas.drawCircle(x, y, outer, line); canvas.drawCircle(x, y, inner, line);
        Path ring = new Path(); ring.addCircle(x, y, 38 * dp, Path.Direction.CW);
        label.setTextSize(9 * dp); label.setLetterSpacing(0.14f); label.setTextAlign(Paint.Align.LEFT);
        canvas.drawTextOnPath("IMMIGRATION · SYDNEY · AUSTRALIA ·", ring, 0, 0, label);
        label.setTextAlign(Paint.Align.CENTER); label.setLetterSpacing(0);
        text(canvas, "ARRIVED", x, y - 9 * dp, 8);
        text(canvas, "12 MAR", x, y + 5 * dp, 12);
        text(canvas, "2026", x, y + 17 * dp, 9);
        canvas.restore();
    }
    private void box(Canvas canvas, float x, float y, float angle) {
        canvas.save(); canvas.rotate(angle, x, y);
        RectF frame = new RectF(x - 42 * dp, y - 24 * dp, x + 42 * dp, y + 24 * dp);
        canvas.drawRoundRect(frame, 4 * dp, 4 * dp, line);
        canvas.drawLine(frame.left + 6 * dp, y - 9 * dp, frame.right - 6 * dp, y - 9 * dp, line);
        text(canvas, "KYOTO JAPAN", x, y - 13 * dp, 7);
        text(canvas, "04 NOV 2026", x, y + 6 * dp, 10);
        text(canvas, "DEPARTED", x, y + 17 * dp, 7);
        canvas.restore();
    }
    private void oval(Canvas canvas, float x, float y, float angle) {
        canvas.save(); canvas.rotate(angle, x, y);
        canvas.drawOval(new RectF(x - 44 * dp, y - 26 * dp, x + 44 * dp, y + 26 * dp), line);
        canvas.drawOval(new RectF(x - 39 * dp, y - 21 * dp, x + 39 * dp, y + 21 * dp), line);
        text(canvas, "BALI", x, y - 3 * dp, 12);
        text(canvas, "VISITOR · 2025", x, y + 10 * dp, 7);
        canvas.restore();
    }
    private void text(Canvas canvas, String value, float x, float y, int size) {
        label.setTextSize(size * dp);
        canvas.drawText(value, x, y, label);
    }
}
