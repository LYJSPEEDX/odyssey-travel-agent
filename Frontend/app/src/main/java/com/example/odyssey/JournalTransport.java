package com.example.odyssey;

import android.widget.LinearLayout;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 将同一种 transport option 按交通类型画成不同卡片：含航班时用票券，否则用紧凑路线卡。
 * option.legs 是按顺序排列的路段，既可只有一次乘车，也可含多次飞行与地面换乘。
 */
final class JournalTransport {
    private final JournalUi journal;
    private final ItineraryDocument doc;
    JournalTransport(JournalUi journal, ItineraryDocument doc) { this.journal = journal; this.doc = doc; }
    void render(LinearLayout lane, JSONObject option, boolean ongoing, Runnable details) throws JSONException {
        JSONArray legs = option.getJSONArray("legs");
        boolean flight = false;
        for (int i = 0; i < legs.length(); i++) flight |= legs.getJSONObject(i).getString("mode").equals("FLIGHT");
        LinearLayout card = journal.card(lane, R.color.surface, details);
        if (ongoing) journal.tag(card, "In transit · Continues from an earlier day", R.color.mint);
        if (!flight) {
            JSONObject first = legs.getJSONObject(0), last = legs.getJSONObject(legs.length() - 1);
            // 这里只加路段移动时长，不包含换乘等待；多段路线明确标注 moving time。
            int minutes = 0;
            for (int i = 0; i < legs.length(); i++) minutes += legs.getJSONObject(i).getInt("durationMinutes");
            journal.text(card, label(first.getString("mode")) + "  ·  " + JournalTime.duration(minutes)
                    + (legs.length() > 1 ? " moving time" : ""), 13, true);
            journal.text(card, doc.place(first.getString("fromPlaceId")) + " → " + doc.place(last.getString("toPlaceId")), 13, false);
            journal.text(card, "Route details  ↗", 11, false);
            return;
        }
        for (int i = 0; i < legs.length(); i++) {
            JSONObject leg = legs.getJSONObject(i), detail = leg.getJSONObject("details");
            if (i > 0) journal.rule(card);
            if (!leg.getString("mode").equals("FLIGHT")) {
                journal.text(card, label(leg.getString("mode")) + " · " + doc.place(leg.getString("fromPlaceId"))
                        + " → " + doc.place(leg.getString("toPlaceId")), 13, true);
                continue;
            }
            journal.tag(card, "FLIGHT · " + leg.getString("timingStatus"), R.color.mint);
            journal.text(card, detail.isNull("marketingAirline") ? "Airline to confirm" : detail.getString("marketingAirline"), 12, false)
                    .setPadding(0, journal.ui.dp(10), 0, 0);
            journal.heading(card, detail.getString("departureAirportCode") + "  →  " + detail.getString("arrivalAirportCode"), 27);
            LinearLayout clocks = journal.row(); card.addView(clocks);
            if (journal.stacked()) clocks.setOrientation(LinearLayout.VERTICAL);
            endpoint(clocks, leg.getJSONObject("departure"), "Departs"); endpoint(clocks, leg.getJSONObject("arrival"), "Arrives");
            journal.text(card, JournalTime.duration(leg.getInt("durationMinutes")) + " · Flight "
                    + (detail.isNull("flightNumber") ? "to confirm" : detail.getString("flightNumber")), 12, false);
            if (detail.isNull("isSelfTransfer")) journal.text(card, "Connection protection to confirm", 12, false);
            if (detail.optBoolean("isSelfTransfer", false)) journal.text(card, "Self-transfer · Check entry and connection", 12, true);
        }
        journal.text(card, "Flight & baggage details  ↗", 12, true);
    }
    /** 出发/到达各自显示当地日期和时区，不用两端钟表读数相减来估算飞行时长。 */
    private void endpoint(LinearLayout row, JSONObject moment, String label) throws JSONException {
        LinearLayout column = journal.column();
        row.addView(column, journal.stacked() ? new LinearLayout.LayoutParams(-1, -2) : new LinearLayout.LayoutParams(0, -2, 1));
        journal.text(column, label, 10, false);
        journal.text(column, JournalTime.clock(moment.getString("at")), 20, true);
        journal.text(column, JournalTime.date(moment.getString("at")), 12, false);
        journal.text(column, moment.getString("timeZone").replace('_', ' '), 10, false);
    }
    private String label(String mode) {
        return mode.substring(0, 1) + mode.substring(1).toLowerCase(java.util.Locale.ENGLISH);
    }
}
