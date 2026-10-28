package com.example.odyssey;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.view.View;
import androidx.core.content.ContextCompat;

/** 装饰性的移动航点，不表达百分比；视图离开屏幕时停止动画。 */
final class GenerationRouteView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;
    private float position = .5f;
    GenerationRouteView(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (android.os.Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) return;
        animator = ValueAnimator.ofFloat(0f, 1f); animator.setDuration(3500);
        animator.setRepeatCount(ValueAnimator.INFINITE); animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        animator.addUpdateListener(value -> { position = (float) value.getAnimatedValue(); invalidate(); });
        animator.start();
    }
    @Override protected void onDetachedFromWindow() { if (animator != null) animator.cancel(); animator = null; super.onDetachedFromWindow(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float start = 20 * density, end = getWidth() - 20 * density, y = getHeight() / 2f;
        paint.setColor(ContextCompat.getColor(getContext(), R.color.text_secondary)); paint.setStrokeWidth(1.5f * density);
        paint.setPathEffect(new DashPathEffect(new float[]{4 * density, 5 * density}, 0));
        canvas.drawLine(start, y, end, y, paint); paint.setPathEffect(null);
        paint.setColor(ContextCompat.getColor(getContext(), R.color.text_primary));
        canvas.drawCircle(start, y, 3 * density, paint); canvas.drawCircle(end, y, 3 * density, paint);
        paint.setColor(ContextCompat.getColor(getContext(), R.color.purple));
        canvas.drawCircle(start + (end - start) * position, y, 7 * density, paint);
    }
}
