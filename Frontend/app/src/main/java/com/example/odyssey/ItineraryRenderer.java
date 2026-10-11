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
    ItineraryRenderer(HomeUi ui, ItineraryDocument doc, int selectedDay, DayAction action) {
        this.ui = ui; this.doc = doc; this.selectedDay = selectedDay; this.action = action;
    }
    void render() throws JSONException {
        JSONObject root = doc.root, summary = root.getJSONObject("summary");
        ui.text(summary.getString("title"), 28, true);
        ui.text(summary.getString("startDate") + " → " + summary.getString("endDate")
                + " · " + summary.getInt("dayCount") + " days", 14, false);
        ui.card(root.getString("dataMode").equals("MOCK") ? "Demo itinerary · not a booking" : "Proposed itinerary · not a booking",
                summary.getString("subtitle") + "\nStatus: " + label(root.getString("status"))
                        + "\n" + join(summary.getJSONArray("highlights")), R.color.mint, null);
        if (!summary.isNull("coverImageId")) imagePlaceholder(summary.getString("coverImageId"));
        ui.text("Your day-by-day plan", 22, true);
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
            button.setText("Day " + (i + 1)); button.setAllCaps(false);
            button.setTag("contract-day-" + i);
            button.setMinHeight(ui.dp(48)); button.setCornerRadius(ui.dp(24));
            button.setTextColor(ui.color(i == selectedDay ? R.color.on_accent : R.color.text_primary));
            button.setBackgroundTintList(ColorStateList.valueOf(ui.color(i == selectedDay ? R.color.lime : R.color.surface)));
            button.setSelected(i == selectedDay);
            button.setContentDescription("Day " + (i + 1) + (i == selectedDay ? ", selected" : ""));
            button.setOnClickListener(view -> action.select(dayIndex));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2); params.setMarginEnd(ui.dp(8));
            tabs.addView(button, params);
            if (i == selectedDay) active = button;
        }
        final MaterialButton selected = active;
        scroll.post(() -> { if (selected != null) scroll.scrollTo(Math.max(0, selected.getLeft() - ui.dp(16)), 0); });
        JSONObject day = days.getJSONObject(selectedDay);
        ui.text("Day " + day.getInt("dayNumber") + " · " + day.getString("title"), 20, true);
        ui.text(day.getString("date") + " · " + day.getString("timeZone") + "\n" + day.getString("summary"), 14, false);
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
        int color = R.color.surface;
        if (type.equals("TRANSPORT")) {
            JSONObject group = doc.get(event.getString("transportGroupId"));
            JSONObject option = doc.selected(group);
            body += "\n" + (option == null ? "Transport options pending." : route(option));
            color = R.color.mint;
        } else {
            body = moment(event.getJSONObject("start")) + "\n→ " + moment(event.getJSONObject("end"))
                    + "\n" + label(event.getString("timingStatus")) + " · " + label(type) + "\n" + body;
            if (event.has("placeId") && !event.isNull("placeId")) body += "\n" + placeDetails(event.getString("placeId"));
            if (type.equals("STAY")) {
                JSONObject option = doc.selected(doc.get(event.getString("stayGroupId")));
                body += "\n" + label(event.getString("action")) + " · " + (option == null ? "Stay pending" : option.getString("title"));
                color = R.color.purple;
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
        ui.card(title, body, color, null);
        JSONArray images = event.getJSONArray("imageIds");
        if (images.length() > 0) imagePlaceholder(images.getString(0));
    }
    private void transport() throws JSONException {
        JSONArray groups = doc.root.getJSONArray("transportGroups");
        if (groups.length() == 0) return;
        ui.text("Transport options", 22, true);
        ui.text("Compare proposals below. Alternatives are not bookings; changing the plan requires recalculation.", 14, false);
        for (int i = 0; i < groups.length(); i++) {
            JSONObject group = groups.getJSONObject(i), selected = doc.selected(group);
            String description = doc.place(group.getString("fromPlaceId")) + " → " + doc.place(group.getString("toPlaceId"));
            JSONArray options = group.getJSONArray("options");
            if (options.length() == 0) {
                ui.card(group.getString("title"), description + "\n" + label(group.getString("status")), R.color.surface, null);
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
            ui.card(title, description + "\n" + options.length() + " options · Tap to compare", R.color.surface, () -> dialog(title, all));
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
                ui.card(option.getString("title"), body, planned ? R.color.purple : R.color.surface, null);
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
                        + "\n" + join(budget.getJSONArray("notes")), R.color.lime, null);
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
        String code = range.getString("currency");
        int digits = -1;
        JSONArray currencies = doc.root.getJSONObject("money").getJSONArray("currencies");
        for (int i = 0; i < currencies.length(); i++) if (currencies.getJSONObject(i).getString("code").equals(code))
            digits = currencies.getJSONObject(i).getInt("minorUnitDigits");
        if (digits < 0) throw new JSONException("Unknown currency precision");
        return code + " " + decimal(range.getLong("expectedMinor"), digits) + " ("
                + decimal(range.getLong("lowMinor"), digits) + "–" + decimal(range.getLong("highMinor"), digits) + ")";
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
        ui.card(title, "Tap to read", R.color.surface, () -> dialog(title, body));
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
    private void imagePlaceholder(String id) throws JSONException {
        JSONObject media = doc.get(id);
        ui.card("Photo preview", media.getString("altText") + "\nImages are not loaded in this offline version.", R.color.surface, null);
    }
    private static String moment(JSONObject value) throws JSONException {
        return value.getString("at").replace('T', ' ') + " · " + value.getString("timeZone");
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
