package com.example.odyssey;

import android.content.res.ColorStateList;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.util.Locale;

/** Renders domain types, not server-provided layouts, HTML or executable links. */
final class ItineraryRenderer {
    interface DayAction { void select(int index); }
    private final HomeUi ui;
    private final ItineraryDocument doc;
    private final int selectedDay;
    private final DayAction action;
    private final JournalUi journal;
    ItineraryRenderer(HomeUi ui, ItineraryDocument doc, int selectedDay, DayAction action) {
        this.ui = ui; this.doc = doc; this.selectedDay = selectedDay; this.action = action;
        journal = new JournalUi(ui);
    }
    void render() throws JSONException {
        JSONObject root = doc.root, summary = root.getJSONObject("summary");
        LinearLayout header = journal.column(); header.setTag("journal-header"); ui.content.addView(header);
        LinearLayout headerActions = journal.row(); header.addView(headerActions);
        if (journal.stacked()) headerActions.setOrientation(LinearLayout.VERTICAL);
        headerActions.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        journal.tag(headerActions, root.getString("dataMode").equals("MOCK") ? "DEMO · NOT BOOKED" : "PROPOSED · NOT BOOKED", R.color.mint);
        android.view.View spacer = new android.view.View(ui.context);
        if (!journal.stacked()) headerActions.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        StringBuilder overview = new StringBuilder(summary.getString("title"))
                .append("\n\n").append(summary.getString("subtitle"));
        JSONArray highlights = summary.getJSONArray("highlights");
        for (int i = 0; i < highlights.length(); i++) overview.append("\n\n• ").append(highlights.getString(i));
        journal.textAction(headerActions, "Overview ↗", () -> dialog("Trip overview", overview.toString()));
        JSONObject request = root.getJSONObject("requestSnapshot");
        JSONArray destinations = request.getJSONArray("destinationPlaceIds");
        StringBuilder destination = new StringBuilder();
        for (int i = 0; i < destinations.length(); i++) {
            if (i > 0) destination.append(" / ");
            destination.append(doc.place(destinations.getString(i)));
        }
        android.widget.TextView title = journal.heading(header, destination.toString(), 34);
        title.setPadding(0, ui.dp(14), 0, 0);
        journal.text(header, "From " + doc.place(request.getString("originPlaceId")) + " · "
                + JournalTime.date(summary.getString("startDate")) + " – " + JournalTime.date(summary.getString("endDate"), "d MMM yyyy"), 14, false);
        JSONObject budget = root.getJSONObject("budget");
        journal.text(header, summary.getInt("dayCount") + " days  ·  " + label(request.getString("companions"))
                + "  ·  " + label(root.getString("status")), 12, false);
        if (budget.getJSONArray("activeCostItemIds").length() > 0)
            journal.text(header, money(budget.getJSONObject("total"), false) + " est. · Entire party"
                    + (budget.getString("completeness").equals("PARTIAL") ? " · Partial" : ""), 14, true);
        JSONArray notices = root.getJSONObject("quality").getJSONArray("notices");
        StringBuilder warnings = new StringBuilder();
        StringBuilder blocking = new StringBuilder();
        int warningCount = 0;
        for (int i = 0; i < notices.length(); i++) {
            JSONObject notice = notices.getJSONObject(i);
            if (!notice.getString("severity").equals("INFO")) {
                warningCount++;
                warnings.append(notice.getString("severity")).append(" · ").append(notice.getString("message")).append("\n");
                if (notice.getString("severity").equals("BLOCKING")) blocking.append(notice.getString("message")).append("\n");
            }
        }
        if (warnings.length() > 0) {
            String warningText = warnings.toString().trim();
            LinearLayout notice = journal.card(ui.content, blocking.length() > 0 ? R.color.red : R.color.surface,
                    () -> dialog("Travel warnings", warningText));
            android.widget.TextView warningTitle = journal.text(notice, warningCount + " travel checks to review  ↗", 13, true);
            if (blocking.length() > 0) {
                warningTitle.setTextColor(ui.color(R.color.on_accent));
                journal.text(notice, blocking.toString().trim(), 13, false).setTextColor(ui.color(R.color.on_accent));
            }
        }
        journal.rule(ui.content);
        journal.heading(ui.content, "Your itinerary", 20);
        JSONArray days = root.getJSONArray("days");
        HorizontalScrollView scroll = new HorizontalScrollView(ui.context);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(ui.context);
        scroll.addView(tabs);
        ui.content.addView(scroll);
        MaterialButton active = null;
        for (int i = 0; i < days.length(); i++) {
            final int dayIndex = i;
            MaterialButton button = new MaterialButton(ui.context);
            button.setText(ui.context.getString(R.string.itinerary_day_tab, i + 1) + "\n"
                    + JournalTime.date(days.getJSONObject(i).getString("date"), "EEE d")); button.setAllCaps(false);
            button.setTextSize(12); button.setMinWidth(ui.dp(68)); button.setMinHeight(ui.dp(64));
            button.setTag("contract-day-" + i);
            button.setCornerRadius(ui.dp(16));
            button.setTextColor(ui.color(i == selectedDay ? R.color.on_accent : R.color.text_primary));
            button.setBackgroundTintList(ColorStateList.valueOf(ui.color(i == selectedDay ? R.color.lime : R.color.surface)));
            button.setSelected(i == selectedDay);
            button.setContentDescription("Day " + (i + 1) + ", "
                    + JournalTime.date(days.getJSONObject(i).getString("date"), "d MMM yyyy")
                    + (i == selectedDay ? ", selected" : ""));
            button.setOnClickListener(view -> action.select(dayIndex));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2); params.setMarginEnd(ui.dp(8));
            tabs.addView(button, params);
            if (i == selectedDay) active = button;
        }
        final MaterialButton selected = active;
        scroll.post(() -> { if (selected != null) scroll.scrollTo(Math.max(0, selected.getLeft() - ui.dp(16)), 0); });
        JSONObject day = days.getJSONObject(selectedDay);
        ui.text(day.getString("title"), 20, true);
        String dayNotes = day.getString("summary") + "\n\nTime zone: " + day.getString("timeZone");
        journal.textAction(ui.content, JournalTime.date(day.getString("date"), "EEEE, d MMM") + " · "
                + day.getJSONArray("events").length() + " stops  ↗", () -> dialog("Day notes", dayNotes))
                .setTag("journal-day-notes");
        if (!day.isNull("walkingDistanceMeters")) ui.text("Walking · " + distance(day.getLong("walkingDistanceMeters")), 14, false);
        JSONArray ongoing = day.getJSONArray("continuingEventIds");
        for (int i = 0; i < ongoing.length(); i++) event(doc.get(ongoing.getString(i)), true);
        JSONArray events = day.getJSONArray("events");
        for (int i = 0; i < events.length(); i++) event(events.getJSONObject(i), false);
        if (events.length() == 0 && ongoing.length() == 0) ui.text("No scheduled stops. Keep this day flexible.", 16, false);
        transport();
        stays();
        budget();
        travelNotes();
    }
    private void event(JSONObject event, boolean ongoing) throws JSONException {
        String type = event.getString("type");
        String title = (ongoing ? "In transit · " : "") + event.getString("title");
        String body = event.getString("description");
        String stamp;
        JSONObject transportOption = null;
        if (type.equals("TRANSPORT")) {
            JSONObject group = doc.get(event.getString("transportGroupId"));
            JSONObject option = doc.selected(group);
            transportOption = option;
            stamp = option == null ? "—" : JournalTime.clock(option.getJSONArray("legs").getJSONObject(0)
                    .getJSONObject("departure").getString("at"));
            body += "\n" + (option == null ? "Transport options pending." : route(option));
        } else {
            stamp = JournalTime.clock(event.getJSONObject("start").getString("at"));
            body = moment(event.getJSONObject("start")) + "\n→ " + moment(event.getJSONObject("end"))
                    + "\n" + label(event.getString("timingStatus")) + " · " + label(type) + "\n" + body;
            if (event.has("placeId") && !event.isNull("placeId")) body += "\n" + placeDetails(event.getString("placeId"));
            if (type.equals("STAY")) {
                JSONObject option = doc.selected(doc.get(event.getString("stayGroupId")));
                body += "\n" + label(event.getString("action")) + " · " + (option == null ? "Stay pending" : option.getString("title"));
            }
            JSONObject details = event.optJSONObject("details");
            if (details != null) {
                if (type.equals("MEAL")) body += "\n" + label(details.getString("mealType")) + " · " + details.getString("cuisine")
                        + "\nDietary suitability: " + label(details.getString("dietaryStatus"));
                if (type.equals("ACTIVITY")) body += "\n" + label(details.getString("category"))
                        + "\n" + optional(details, "openingHoursNote", "Opening hours unverified");
                body += "\nReservation: " + label(details.getString("reservationStatus"));
            }
            if (event.has("costItemIds")) body += costs(event.getJSONArray("costItemIds"));
        }
        LinearLayout lane = journal.timeline(ongoing ? "↳" : stamp);
        String detailTitle = title, detailBody = body;
        Runnable open = () -> dialog(detailTitle, detailBody);
        if (type.equals("TRANSPORT") && transportOption != null) {
            new JournalTransport(journal, doc).render(lane, transportOption, ongoing, open);
        } else {
            LinearLayout card = journal.card(lane, R.color.surface, open);
            journal.tag(card, type.equals("STAY") ? "STAY" : type.equals("MEAL") ? "FOOD & DRINK" : label(type).toUpperCase(Locale.ENGLISH),
                    type.equals("STAY") ? R.color.purple : type.equals("MEAL") ? R.color.mint : R.color.background);
            journal.heading(card, title, 18).setPadding(0, ui.dp(10), 0, 0);
            android.widget.TextView description = journal.text(card, event.getString("description"), 13, false);
            description.setMaxLines(3); description.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (event.has("placeId") && !event.isNull("placeId")) journal.text(card, doc.place(event.getString("placeId")), 12, false);
            journal.text(card, "View details  ↗", 12, true);
        }
    }
    private void transport() throws JSONException {
        JSONArray groups = doc.root.getJSONArray("transportGroups");
        if (groups.length() == 0) return;
        LinearLayout comparisons = journal.column();
        comparisons.setVisibility(android.view.View.GONE);
        journal.disclosure("Transport options", groups.length() + " routes · Tap to expand comparisons",
                () -> comparisons.setVisibility(comparisons.getVisibility() == android.view.View.VISIBLE
                        ? android.view.View.GONE : android.view.View.VISIBLE));
        ui.content.addView(comparisons);
        journal.text(comparisons, "Alternatives are not bookings; changing the plan requires recalculation.", 14, false);
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.getJSONObject(i), selected = doc.selected(group);
            String description = doc.place(group.getString("fromPlaceId")) + " → " + doc.place(group.getString("toPlaceId"));
            JSONArray options = group.getJSONArray("options");
            if (options.length() == 0) {
                LinearLayout pending = journal.card(comparisons, R.color.surface, null);
                journal.text(pending, group.getString("title"), 16, true);
                journal.text(pending, description + "\n" + label(group.getString("status")), 12, false);
                continue;
            }
            StringBuilder comparison = new StringBuilder(description);
            for (int j = 0; j < options.length(); j++) {
                JSONObject option = options.getJSONObject(j);
                comparison.append("\n\n").append(option.getString("title"));
                if (selected != null && option.getString("id").equals(selected.getString("id"))) comparison.append(" · In this plan");
                comparison.append("\n").append(option.getString("reason")).append("\n").append(route(option))
                        .append(costs(option.getJSONArray("costItemIds")));
            }
            String title = group.getString("title"), all = comparison.toString();
            LinearLayout comparisonCard = journal.card(comparisons, R.color.surface, () -> dialog(title, all));
            journal.text(comparisonCard, title + "  ↗", 16, true);
            journal.text(comparisonCard, options.length() + (options.length() == 1 ? " option" : " options") + " · Compare routes & fares", 12, false);
        }
    }
    private String route(JSONObject option) throws JSONException {
        StringBuilder result = new StringBuilder();
        JSONArray legs = option.getJSONArray("legs");
        for (int i = 0; i < legs.length(); i++) {
            JSONObject leg = legs.getJSONObject(i), detail = leg.getJSONObject("details");
            String mode = leg.getString("mode");
            if (i > 0) result.append("\n\nTransfer\n");
            result.append(label(mode)).append(" · ").append(doc.place(leg.getString("fromPlaceId")))
                    .append(" → ").append(doc.place(leg.getString("toPlaceId")))
                    .append("\n").append(moment(leg.getJSONObject("departure")))
                    .append("\n→ ").append(moment(leg.getJSONObject("arrival")))
                    .append("\n").append(leg.getInt("durationMinutes")).append(" min · ")
                    .append(label(leg.getString("timingStatus")));
            if (!leg.isNull("distanceMeters")) result.append(" · ").append(distance(leg.getLong("distanceMeters")))
                    .append(" (").append(label(leg.getString("distanceBasis"))).append(")");
            if (mode.equals("FLIGHT")) {
                result.append("\n").append(detail.getString("departureAirportCode")).append(" → ").append(detail.getString("arrivalAirportCode"))
                        .append("\nAirline: ").append(optional(detail, "marketingAirline", "To be confirmed"))
                        .append(" · Flight: ").append(optional(detail, "flightNumber", "To be confirmed"))
                        .append("\nOperating airline: ").append(optional(detail, "operatingAirline", "To be confirmed"))
                        .append("\nBaggage: ").append(optional(detail, "checkedBaggageKgPerPerson", "Unknown")).append(" kg per person");
                if (detail.getBoolean("isSelfTransfer")) result.append("\nSelf-transfer — verify connection and entry requirements.");
            } else {
                if (detail.has("operator")) result.append("\nOperator: ").append(optional(detail, "operator", "To be confirmed"));
                result.append("\n").append(detail.getString("routeInstructions"));
                if (mode.equals("DRIVE")) result.append("\n").append(label(detail.getString("vehicleSource")));
                if (mode.equals("WALK")) result.append("\n").append(detail.getString("accessibilityNotes"));
            }
        }
        return result.toString();
    }
    private void stays() throws JSONException {
        ui.text("Where to stay", 22, true);
        JSONArray groups = doc.root.getJSONArray("stayGroups");
        if (groups.length() == 0) ui.text("Stay recommendations pending. No accommodation has been booked.", 14, false);
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.getJSONObject(i), selected = doc.selected(group);
            ui.text(group.getString("title") + "\n" + group.getString("checkInDate") + " → " + group.getString("checkOutDate")
                    + " · " + group.getInt("nights") + " nights", 16, true);
            JSONArray options = group.getJSONArray("options");
            if (options.length() == 0) ui.text(label(group.getString("status")), 14, false);
            LinearLayout rail = journal.rail("hotel-carousel");
            for (int j = 0; j < options.length(); j++) {
                JSONObject option = options.getJSONObject(j);
                boolean planned = selected != null && selected.getString("id").equals(option.getString("id"));
                String body = (planned ? "In this plan · not booked\n" : "Alternative · not included in total\n")
                        + option.getString("reason") + "\n" + placeDetails(option.getString("placeId"))
                        + "\n" + option.getInt("roomCount") + " × " + option.getString("roomType")
                        + " · Capacity: " + option.getInt("guestCapacity")
                        + "\n" + join(option.getJSONArray("amenities"))
                        + "\nAvailability: " + label(option.getString("availability"))
                        + "\n" + optional(option, "cancellationSummary", "Cancellation policy unverified")
                        + costs(option.getJSONArray("costItemIds"));
                String hotelTitle = option.getString("title"), details = body;
                LinearLayout holder = journal.column();
                int width = Math.max(200, Math.min(272, ui.context.getResources().getConfiguration().screenWidthDp - 56));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ui.dp(width), -2); params.setMarginEnd(ui.dp(12));
                rail.addView(holder, params);
                LinearLayout card = journal.card(holder, R.color.surface, () -> dialog(hotelTitle, details));
                journal.tag(card, planned ? "IN YOUR PLAN" : "ALTERNATIVE " + (j + 1), planned ? R.color.purple : R.color.background);
                journal.heading(card, hotelTitle, 18).setPadding(0, ui.dp(12), 0, 0);
                android.widget.TextView reason = journal.text(card, option.getString("reason"), 13, false);
                reason.setMaxLines(2); reason.setEllipsize(android.text.TextUtils.TruncateAt.END);
                journal.rule(card);
                journal.text(card, option.getInt("roomCount") + " room(s) · " + group.getInt("nights") + " nights", 12, false);
                journal.text(card, "Not booked · Details & price  ↗", 12, true);
            }
        }
    }
    private void budget() throws JSONException {
        JSONObject budget = doc.root.getJSONObject("budget");
        ui.text("Estimated budget", 22, true);
        JSONArray active = budget.getJSONArray("activeCostItemIds");
        ui.card(active.length() == 0 ? "Estimate pending" : money(budget.getJSONObject("total")),
                "Entire party · whole trip\n" + (budget.getString("completeness").equals("PARTIAL")
                        ? "Partial estimate — excludes unpriced items." : "Estimate only — not a quote.")
                        + "\n" + join(budget.getJSONArray("notes")), R.color.surface, null);
        StringBuilder details = new StringBuilder("Known subtotal: ").append(money(budget.getJSONObject("subtotal")))
                .append("\nContingency: ").append(money(budget.getJSONObject("contingency"))).append(costs(active));
        if (budget.getJSONArray("excludedCategories").length() > 0)
            details.append("\nExcluded: ").append(join(budget.getJSONArray("excludedCategories")));
        disclosure("Cost breakdown", details.toString());
    }
    private String costs(JSONArray ids) throws JSONException {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < ids.length(); i++) {
            JSONObject item = doc.get(ids.getString(i));
            result.append("\n").append(item.getString("label")).append(" · ")
                    .append(item.isNull("displayTotal") ? "Price pending" : money(item.getJSONObject("displayTotal")))
                    .append("\n").append(item.getInt("quantity")).append(" × ").append(label(item.getString("unit")))
                    .append(" · Taxes/fees: ").append(label(item.getString("taxesAndFees")));
        }
        return result.toString();
    }
    private String money(JSONObject range) throws JSONException {
        return money(range, true);
    }
    private String money(JSONObject range, boolean includeRange) throws JSONException {
        String code = range.getString("currency");
        int digits = -1;
        JSONArray currencies = doc.root.getJSONObject("money").getJSONArray("currencies");
        for (int i = 0; i < currencies.length(); i++) if (currencies.getJSONObject(i).getString("code").equals(code))
            digits = currencies.getJSONObject(i).getInt("minorUnitDigits");
        if (digits < 0) throw new JSONException("Unknown currency precision");
        return code + " " + decimal(range.getLong("expectedMinor"), digits) + (includeRange ? " ("
                + decimal(range.getLong("lowMinor"), digits) + "–" + decimal(range.getLong("highMinor"), digits) + ")" : "");
    }
    private void travelNotes() throws JSONException {
        ui.text("Before you go", 22, true);
        JSONObject money = doc.root.getJSONObject("money"), quality = doc.root.getJSONObject("quality");
        StringBuilder exchange = new StringBuilder("Display currency: ").append(money.getString("displayCurrency"));
        JSONArray rates = money.getJSONArray("exchangeRates");
        if (rates.length() == 0) exchange.append("\nNo exchange rates supplied.");
        for (int i = 0; i < rates.length(); i++) {
            JSONObject rate = rates.getJSONObject(i);
            exchange.append("\n1 ").append(rate.getString("baseCurrency")).append(" ≈ ").append(rate.getString("quotePerBase"))
                    .append(" ").append(rate.getString("quoteCurrency")).append(" · ")
                    .append(label(rate.getJSONObject("evidence").getString("status")))
                    .append("\nAs of: ").append(optional(rate, "asOf", "Not verified"));
        }
        JSONArray advice = money.getJSONArray("exchangeAdvice");
        for (int i = 0; i < advice.length(); i++) exchange.append("\n\n").append(advice.getJSONObject(i).getString("title"))
                .append("\n").append(advice.getJSONObject(i).getString("description"));
        disclosure("Currency & exchange", exchange.toString());
        JSONArray entries = doc.root.getJSONArray("entryRequirements");
        StringBuilder entry = new StringBuilder("Entry requirements depend on each traveler and transit route. Verify with official sources.");
        if (entries.length() == 0) entry.append("\nNo verified requirements supplied.");
        for (int i = 0; i < entries.length(); i++) {
            JSONObject rule = entries.getJSONObject(i);
            entry.append("\n\n").append(rule.getString("countryCode")).append(" · ").append(label(rule.getString("role")))
                    .append(" · ").append(label(rule.getString("status"))).append("\n").append(rule.getString("summary"))
                    .append("\nMissing information: ").append(join(rule.getJSONArray("requiredInputFields")))
                    .append("\nDocuments: ").append(join(rule.getJSONArray("documents")));
        }
        disclosure("Entry & visa checks", entry.toString());
        StringBuilder notes = new StringBuilder(join(doc.root.getJSONObject("summary").getJSONArray("advice")))
                .append("\n\nAssumptions\n").append(join(quality.getJSONArray("assumptions")))
                .append("\n\nYour notes\n").append(join(doc.root.getJSONObject("requestSnapshot").getJSONArray("notes")));
        JSONArray notices = quality.getJSONArray("notices");
        for (int i = 0; i < notices.length(); i++) notes.append("\n\n").append(notices.getJSONObject(i).getString("severity"))
                .append(" · ").append(notices.getJSONObject(i).getString("message"));
        JSONArray checks = quality.getJSONArray("constraintChecks");
        for (int i = 0; i < checks.length(); i++) notes.append("\n\n").append(label(checks.getJSONObject(i).getString("status")))
                .append(" · ").append(checks.getJSONObject(i).getString("explanation"));
        JSONArray sources = quality.getJSONArray("sources");
        for (int i = 0; i < sources.length(); i++) notes.append("\n\nSource: ").append(sources.getJSONObject(i).getString("title"))
                .append("\n").append(sources.getJSONObject(i).getString("url"));
        disclosure("Advice, preferences & sources", notes.toString());
    }
    private void disclosure(String title, String body) {
        journal.disclosure(title, "", () -> dialog(title, body));
    }
    private void dialog(String title, String body) {
        new MaterialAlertDialogBuilder(ui.context).setTitle(title).setMessage(body).setPositiveButton("Close", null).show();
    }
    private String placeDetails(String id) throws JSONException {
        JSONObject place = doc.get(id);
        String value = place.getString("name");
        if (!place.isNull("address")) value += "\n" + place.getString("address");
        JSONObject point = place.optJSONObject("coordinates");
        if (point != null) value += String.format(Locale.US, "\nLocation: %.4f, %.4f", point.getDouble("latitude"), point.getDouble("longitude"));
        return value;
    }
    private static String moment(JSONObject value) throws JSONException {
        return JournalTime.date(value.getString("at"), "d MMM yyyy") + " · " + JournalTime.clock(value.getString("at"))
                + " · " + value.getString("timeZone");
    }
    private static String optional(JSONObject object, String key, String fallback) throws JSONException {
        return object.isNull(key) ? fallback : String.valueOf(object.get(key));
    }
    private static String join(JSONArray array) throws JSONException {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < array.length(); i++) { if (i > 0) result.append("\n"); result.append(array.getString(i)); }
        return result.toString();
    }
    private static String decimal(long value, int digits) { return BigDecimal.valueOf(value, digits).toPlainString(); }
    private static String distance(long meters) { return String.format(Locale.US, "%.1f km", meters / 1000.0); }
    private static String label(String value) {
        String lower = value.toLowerCase(Locale.ENGLISH).replace('_', ' ');
        return lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
