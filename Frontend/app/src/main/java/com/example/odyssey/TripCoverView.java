package com.example.odyssey;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import com.google.android.material.card.MaterialCardView;
import org.json.JSONObject;

/** 详情页的大封面；与列表共用 TripPhotos，保证进入详情后仍看到同一个目的地。 */
final class TripCoverView {
    static void add(HomeUi ui, LinearLayout parent, String city, String title, JSONObject root) {
        MaterialCardView card = new MaterialCardView(ui.context);
        card.setTag("journal-cover"); // 供界面回归测试定位，不是用户可见文字。
        card.setRadius(ui.dp(28));
        card.setCardElevation(0);
        card.setStrokeWidth(0);
        int height = ui.dp(Math.round(256 * Math.max(1, ui.context.getResources().getConfiguration().fontScale)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, height);
        params.bottomMargin = ui.dp(16);
        parent.addView(card, params);
        FrameLayout layers = new FrameLayout(ui.context);
        card.addView(layers, new FrameLayout.LayoutParams(-1, -1));
        ImageView image = new ImageView(ui.context);
        if (root == null) TripPhotos.show(image, city);
        else TripPhotos.showCover(image, root, city);
        layers.addView(image, new FrameLayout.LayoutParams(-1, -1));
        // 叠加半透明黑色，亮色照片上也能读清白色标题。
        View scrim = new View(ui.context);
        scrim.setBackgroundResource(R.drawable.bg_photo_scrim);
        layers.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
        TextView heading = new TextView(ui.context);
        heading.setText(title);
        heading.setTextSize(34);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setTextColor(ui.color(R.color.on_photo));
        heading.setIncludeFontPadding(false);
        heading.setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(24));
        heading.setMaxLines(4);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        ViewCompat.setAccessibilityHeading(heading, true);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        layers.addView(heading, titleParams);
    }
    private TripCoverView() {}
}
