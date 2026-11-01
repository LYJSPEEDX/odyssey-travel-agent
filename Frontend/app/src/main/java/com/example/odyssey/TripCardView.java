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
        return add(ui, trip, shortCity(TripPhotos.destination(trip)), ui.context.getString(R.string.editorial_saved), shortRoute(trip.route), "Your next chapter", open);
    }
    static MaterialCardView addSample(HomeUi ui, SavedTrip trip, String title, String keywords, Runnable open) {
        return add(ui, trip, title, ui.context.getString(R.string.editorial_sample), trip.route, keywords, open);
    }
    private static MaterialCardView add(HomeUi ui, SavedTrip trip, String title, String badge, String route, String keywords, Runnable open) {
        MaterialCardView card = (MaterialCardView) LayoutInflater.from(ui.context)
                .inflate(R.layout.item_editorial_trip, ui.content, false);
        String city = shortCity(TripPhotos.destination(trip));
        ImageView photo = card.findViewById(R.id.editorialPhoto);
        TripPhotos.show(photo, city);
        if (trip.contractJson != null) try {
            JSONObject root = new JSONObject(trip.contractJson);
            TripPhotos.showResponse(photo, ResponseImage.cover(root, true, System.currentTimeMillis()), TripPhotos.fallbackCity(root, city));
        } catch (JSONException ignored) { /* 兼容旧快照；详情页负责报告不可读的 contract。 */ }
        text(card, R.id.editorialTitle, title);
        text(card, R.id.editorialEyebrow, TripPhotos.resource(city) == R.drawable.bg_photo_placeholder
                ? ui.context.getString(R.string.editorial_photo_placeholder) : "A LITTLE FURTHER FROM EVERYDAY");
        text(card, R.id.editorialBadge, badge);
        // 显示真实的城市路线，不凭目的地猜机场代码，也不会给驾车行程加飞机图标。
        text(card, R.id.editorialRoute, route);
        text(card, R.id.editorialDuration, ui.context.getResources().getQuantityString(
                R.plurals.editorial_days, trip.totalDays, trip.totalDays));
        text(card, R.id.editorialKeywords, keywords);
        ((TextView) card.findViewById(R.id.editorialKeywords)).setMaxLines(1);
        text(card, R.id.editorialDates, displayDates(trip.dates));
        // 字体放大时增加封面高度，不能让标题覆盖顶部标签。
        View cover = card.findViewById(R.id.editorialCover);
        cover.getLayoutParams().height = ui.dp(Math.round(232 * Math.max(1, ui.context.getResources().getConfiguration().fontScale)));
        ViewCompat.setAccessibilityHeading(card.findViewById(R.id.editorialTitle), true);
        card.setContentDescription(title + ". " + route + ". " + trip.dates + ". "
                + ui.context.getString(R.string.editorial_view_trip));
        card.setOnClickListener(view -> open.run());
        ui.content.addView(card);
        return card;
    }
    private static void text(View parent, int id, String value) { ((TextView) parent.findViewById(id)).setText(value); }
    /** 卡片只放简短信息：长地名取第一段，完整内容在详情页。 */
    private static String shortCity(String place) { return place.split(",")[0].trim(); }
    private static String shortRoute(String route) {
        String[] ends = route.split(" → ");
        return ends.length == 2 ? shortCity(ends[0]) + " → " + shortCity(ends[1]) : route;
    }
    /** 后端日期是 2026-11-11 → 2026-11-14，换成和其他页面一致的写法。 */
    private static String displayDates(String dates) {
        java.util.regex.Matcher iso = java.util.regex.Pattern.compile("\\d{4}-\\d{2}-\\d{2}").matcher(dates);
        StringBuffer result = new StringBuffer();
        while (iso.find()) iso.appendReplacement(result, TripDates.format(Integer.parseInt(iso.group().replace("-", ""))));
        iso.appendTail(result);
        return result.toString();
    }
    private TripCardView() {}
}
