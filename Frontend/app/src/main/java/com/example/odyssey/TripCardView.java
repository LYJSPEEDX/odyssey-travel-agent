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
    static MaterialCardView add(HomeUi ui, SavedTrip trip, boolean sample, Runnable open, Runnable save) {
        MaterialCardView card = (MaterialCardView) LayoutInflater.from(ui.context)
                .inflate(R.layout.item_editorial_trip, ui.content, false);
        String city = TripPhotos.destination(trip);
        ImageView photo = card.findViewById(R.id.editorialPhoto);
        TripPhotos.show(photo, city);
        if (trip.contractJson != null) try {
            android.widget.TextView credit = new android.widget.TextView(ui.context);
            credit.setTextSize(10); credit.setTextColor(ui.color(R.color.text_secondary));
            ((android.widget.LinearLayout) card.findViewById(R.id.editorialTicket)).addView(credit, 0);
            JSONObject root = new JSONObject(trip.contractJson);
            TripPhotos.showResponse(photo, ResponseImage.cover(root, true, System.currentTimeMillis()), TripPhotos.fallbackCity(root, city), credit);
        } catch (JSONException ignored) { /* 兼容旧快照；详情页负责报告不可读的 contract。 */ }
        String title = sample ? sampleTitle(city) : city;
        text(card, R.id.editorialTitle, title);
        text(card, R.id.editorialEyebrow, TripPhotos.resource(city) == R.drawable.bg_photo_placeholder
                ? ui.context.getString(R.string.editorial_photo_placeholder) : "A LITTLE FURTHER FROM EVERYDAY");
        text(card, R.id.editorialBadge, ui.context.getString(sample ? R.string.editorial_sample : R.string.editorial_saved));
        // 显示真实的城市路线，不凭目的地猜机场代码，也不会给驾车行程加飞机图标。
        text(card, R.id.editorialRoute, trip.route);
        text(card, R.id.editorialDuration, ui.context.getResources().getQuantityString(
                R.plurals.editorial_days, trip.totalDays, trip.totalDays));
        text(card, R.id.editorialKeywords, TripPhotos.keywords(city));
        text(card, R.id.editorialDates, trip.dates);
        // 字体放大时增加封面高度，不能让标题覆盖顶部标签。
        View cover = card.findViewById(R.id.editorialCover);
        cover.getLayoutParams().height = ui.dp(Math.round(232 * Math.max(1, ui.context.getResources().getConfiguration().fontScale)));
        ViewCompat.setAccessibilityHeading(card.findViewById(R.id.editorialTitle), true);
        card.setContentDescription(title + ". " + trip.route + ". " + trip.dates + ". "
                + ui.context.getString(R.string.editorial_view_trip));
        card.setOnClickListener(view -> open.run());
        View bookmark = card.findViewById(R.id.editorialSave);
        if (save != null) {
            bookmark.setVisibility(View.VISIBLE);
            bookmark.setOnClickListener(view -> save.run()); // 单独动作只保存，不触发行程跳转。
        }
        ui.content.addView(card);
        return card;
    }
    private static void text(View parent, int id, String value) { ((TextView) parent.findViewById(id)).setText(value); }
    private static String sampleTitle(String city) {
        if ("Kyoto".equals(city)) return "A slower kind\nof Kyoto.";
        if ("Tokyo".equals(city)) return "Tokyo, a fresh\nperspective.";
        if ("Bali".equals(city)) return "Bali, on\nisland time.";
        if ("Canberra".equals(city)) return "A little closer.\nCanberra.";
        if ("New York".equals(city)) return "New York,\nyour way.";
        return city;
    }
    private TripCardView() {}
}
