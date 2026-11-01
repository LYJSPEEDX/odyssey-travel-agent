package com.example.odyssey;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import com.google.android.material.card.MaterialCardView;
import org.json.JSONException;
import org.json.JSONObject;

/** 杂志封面＋登机牌信息区；只展示快照，不在组件里生成、预订或修改旅行。 */
final class TripCardView {
    static MaterialCardView add(HomeUi ui, SavedTrip trip, Runnable open) {
        MaterialCardView card = (MaterialCardView) LayoutInflater.from(ui.context)
                .inflate(R.layout.item_editorial_trip, ui.content, false);
        String city = TripPhotos.destination(trip);
        ImageView photo = card.findViewById(R.id.editorialPhoto);
        TripPhotos.show(photo, city);
        if (trip.contractJson != null) try {
            JSONObject root = new JSONObject(trip.contractJson);
            TripPhotos.showResponse(photo, ResponseImage.cover(root, true, System.currentTimeMillis()), TripPhotos.fallbackCity(root, city));
        } catch (JSONException ignored) { /* 兼容旧快照；详情页负责报告不可读的 contract。 */ }
        text(card, R.id.editorialTitle, city);
        text(card, R.id.editorialEyebrow, TripPhotos.resource(city) == R.drawable.bg_photo_placeholder
                ? ui.context.getString(R.string.editorial_photo_placeholder) : "A LITTLE FURTHER FROM EVERYDAY");
        text(card, R.id.editorialBadge, ui.context.getString(R.string.editorial_saved));
        // 显示真实的城市路线，不凭目的地猜机场代码，也不会给驾车行程加飞机图标。
        text(card, R.id.editorialRoute, trip.route);
        text(card, R.id.editorialDuration, ui.context.getResources().getQuantityString(
                R.plurals.editorial_days, trip.totalDays, trip.totalDays));
        text(card, R.id.editorialKeywords, keywords(trip));
        // 行程摘要可能很长，卡片上只留两行，完整内容在详情页。
        TextView keywords = card.findViewById(R.id.editorialKeywords);
        keywords.setMaxLines(2); keywords.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text(card, R.id.editorialDates, trip.dates);
        // 字体放大时增加封面高度，不能让标题覆盖顶部标签。
        View cover = card.findViewById(R.id.editorialCover);
        cover.getLayoutParams().height = ui.dp(Math.round(232 * Math.max(1, ui.context.getResources().getConfiguration().fontScale)));
        ViewCompat.setAccessibilityHeading(card.findViewById(R.id.editorialTitle), true);
        card.setContentDescription(city + ". " + trip.route + ". " + trip.dates + ". "
                + ui.context.getString(R.string.editorial_view_trip));
        card.setOnClickListener(view -> open.run());
        ui.content.addView(card);
        return card;
    }
    private static void text(View parent, int id, String value) { ((TextView) parent.findViewById(id)).setText(value); }
    /** 行程自带的副标题；旧快照没有时用通用文案。 */
    private static String keywords(SavedTrip trip) {
        if (trip.contractJson != null) try {
            String subtitle = new JSONObject(trip.contractJson).getJSONObject("summary").optString("subtitle");
            if (!subtitle.isEmpty()) return subtitle;
        } catch (JSONException ignored) { /* 详情页负责报告不可读的 contract。 */ }
        return "Your next chapter";
    }
    private TripCardView() {}
}
