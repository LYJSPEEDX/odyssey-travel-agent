package com.example.odyssey;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.JSONArray;
import org.json.JSONObject;

/** Live proposal → explicit confirmation → finalize → HTTP generation → local itinerary. */
public class LivePlannerActivity extends AppCompatActivity {
    private LivePlannerModel model;
    private HomeUi ui;
    private AppCompatEditText input;
    private int shownStep = -1;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); EdgeToEdge.enable(this); setContentView(R.layout.activity_live_planner);
        model = LivePlannerModel.get(getApplication());
        ui = new HomeUi(findViewById(R.id.liveContent)); input = findViewById(R.id.liveInput);
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.liveToolbar);
        toolbar.setTitle("Odyssey · Live planner"); toolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        toolbar.setNavigationContentDescription("Back to home"); toolbar.setNavigationOnClickListener(v -> finish());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.liveRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(ui.dp(20), bars.top, ui.dp(20), bars.bottom); return insets;
        });
        findViewById(R.id.liveSend).setOnClickListener(v -> {
            model.draft(input.getText() == null ? "" : input.getText().toString()); model.send(model.draft());
        });
        input.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { model.draft(s.toString()); }
            public void afterTextChanged(android.text.Editable text) { }
        });
        model.updates.observe(this, ignored -> render());
        // Restore is read-only. New sessions require a visible Start action.
        if (model.session != null && !model.busy) model.restore();
    }
    private void render() {
        ui.content.removeAllViews();
        boolean editable = model.session != null && !model.busy && model.pendingTurn == null && !model.generationUncertain;
        findViewById(R.id.liveComposer).setVisibility(model.session == null || !model.savedTripId.isEmpty() ? View.GONE : View.VISIBLE);
        input.setEnabled(editable); findViewById(R.id.liveSend).setEnabled(editable);
        if (shownStep != model.selected || !model.busy) {
            shownStep = model.selected; String draft = model.draft();
            if (!input.getText().toString().equals(draft)) { input.setText(draft); input.setSelection(input.length()); }
        }
        ui.text("LIVE · NOT BOOKED", 12, true);
        if (model.busy) {
            android.widget.ProgressBar progress = new android.widget.ProgressBar(this);
            ui.content.addView(progress, new LinearLayout.LayoutParams(-1, ui.dp(40)));
            ui.text(model.status, 16, true);
            ui.text("You can leave this screen and return while the app stays running. Closing the app may interrupt the connection; the server may still finish.", 13, false);
        }
        if (!model.error.isEmpty()) ui.card("Planner update", model.error, R.color.surface, null);
        if (!model.savedTripId.isEmpty()) {
            ui.text("Your itinerary is saved", 28, true);
            ui.text("Saved on the server and this device. Travel details and partial prices still require verification.", 15, false);
            button("Open itinerary", () -> startActivity(new Intent(this, TripDetailActivity.class)
                    .putExtra(TripDetailActivity.EXTRA_ID, model.savedTripId)), "live-open");
            button("My trips", () -> startActivity(new Intent(this, HomeActivity.class)
                    .putExtra(HomeActivity.EXTRA_OPEN_MY, true).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)), "live-my-trips");
        }
        if (model.session == null && model.savedTripId.isEmpty()) {
            ui.text("Let’s plan a real trip", 28, true);
            ui.text("Discuss each step, review the proposed facts, and confirm them before generating. Your profile’s city, currency and travel preferences are sent to your configured server. Personal demographics stay on this device.", 15, false);
            ui.text("For this demo: adults only, fixed dates, and at least one overnight stay. Generation can take several minutes. Nothing is booked.", 14, false);
            button("Check connection", model::health, "live-health");
            button(model.creation == null ? "Start live planning" : "Restore session creation", model::start, "live-start");
        } else if (model.session != null && model.savedTripId.isEmpty()) {
            try { renderPlanning(); } catch (Exception exception) { ui.text("Cannot display this session. Restore it or check the backend version.", 15, false); }
        }
        if (model.pendingTurn != null) {
            ui.text("A reply needs checking", 20, true);
            ui.text("Check the original reply to avoid submitting it twice. If it is still processing, wait and restore before trying again.", 14, false);
            button("Check original reply", model::replay, "live-replay");
            button("Restore and review before a new attempt", () -> new MaterialAlertDialogBuilder(this)
                    .setTitle("Review before retrying?").setMessage("The earlier request may still be running. Confirmed facts will be restored before another attempt is allowed.")
                    .setNegativeButton("Wait", null).setPositiveButton("Restore", (d, w) -> model.abandonTurn()).show(), "live-abandon");
        }
        if (model.generationUncertain) {
            ui.text("Check generation outcome", 20, true);
            ui.text("The request may still be running or already saved. No automatic generation retry will be sent.", 14, false);
            button("Check saved server trips", model::listTrips, "live-recover");
            if (!model.resultJson.isEmpty()) button("Retry saving received itinerary", model::retrySave, "live-retry-save");
            button("Allow a new generation attempt…", () -> new MaterialAlertDialogBuilder(this)
                    .setTitle("Start another generation?").setMessage("The previous request may still finish. Another attempt can create a duplicate trip and use model/provider quota. Check server trips first.")
                    .setNegativeButton("Keep checking", null).setPositiveButton("I understand", (d, w) -> model.allowAnotherGeneration()).show(), "live-new-attempt");
        }
        for (int i = 0; i < model.serverTrips.length(); i++) {
            JSONObject trip = model.serverTrips.optJSONObject(i);
            if (trip == null) continue;
            String id = trip.optString("id");
            ui.card(trip.optString("title", "Saved trip"), trip.optString("created_at") + " · " + trip.optString("status"),
                    R.color.surface, model.busy ? null : () -> model.recover(id));
        }
        if (!model.generationUncertain) button("Browse saved server trips", model::listTrips, "live-server-trips");
        if (model.session != null) button("Restore session", model::restore, "live-restore");
        button("Connection settings", () -> new BackendSettingsDialog(this, () -> {
            finish(); startActivity(new Intent(this, LivePlannerActivity.class));
        }).show(), "live-settings");
        if ((model.session != null || !model.savedTripId.isEmpty()) && !model.generationUncertain) button("Start a fresh plan…", () -> new MaterialAlertDialogBuilder(this)
                .setTitle("Start a fresh plan?").setMessage("A new plan will replace the active draft on this device. Your previous server session and saved trips will remain.")
                .setNegativeButton("Keep plan", null).setPositiveButton("New plan", (d, w) -> model.newSession()).show(), "live-fresh");
    }
    private void renderPlanning() throws Exception {
        JSONObject session = model.session, steps = session.getJSONObject("steps");
        String step = LivePlanningProtocol.STEPS[model.selected];
        ui.text((model.selected + 1) + " / 8 · " + LivePlanningProtocol.TITLES[model.selected], 26, true);
        ui.text(model.selected == LivePlanningProtocol.index(session.getString("currentStep"))
                ? session.optString("question") : "Review or revise your confirmed answer.", 15, false);
        JSONObject current = steps.getJSONObject(step);
        ui.text("Status: " + current.getString("state").toLowerCase(java.util.Locale.ROOT).replace('_', ' '), 12, false);
        if (current.optJSONObject("answer") != null)
            ui.card("Previous answer", LivePlanningProtocol.answer(step, current.getJSONObject("answer")), R.color.surface, null);
        if (model.selected == 2) button("Choose fixed dates", this::chooseDates, "live-dates");
        String[] hints = {"Example: Departing from Sydney, Australia.", "Example: I’d like Osaka, Japan.",
                "Give fixed departure and destination-departure dates. Same-day and flexible trips cannot generate yet.",
                "Give the exact adult count and say whether any children are travelling. Generation currently supports adults only.",
                "Give currency, amount, per-person/party and per-day/whole-trip scope, and whether flights are included.",
                "State essential versus preferred needs, or explicitly say no extra requirements.",
                "Describe your preferred pace and breaks.", "Describe food, culture, nature or other experiences you want."};
        ui.text(hints[model.selected], 13, false);
        JSONArray messages = current.getJSONArray("messages");
        for (int i = 0; i < messages.length(); i++) { JSONObject message = messages.getJSONObject(i);
            ui.card(message.getString("role").equals("USER") ? "You" : "Planning assistant", message.getString("text"), R.color.surface, null); }
        if (messages.length() == 0 && model.selected == LivePlanningProtocol.index(session.getString("currentStep")) && !session.optString("assistantMessage").isEmpty())
            ui.text(session.getString("assistantMessage"), 15, false);
        JSONObject proposal = session.optJSONObject("proposal");
        if (proposal != null && step.equals(proposal.getString("stepId"))) {
            ui.text("Review before confirming", 20, true);
            JSONArray options = proposal.getJSONArray("options");
            for (int i = 0; i < options.length(); i++) {
                JSONObject option = options.getJSONObject(i); String id = option.getString("id");
                ui.card(option.getString("title"), LivePlanningProtocol.answer(step, option.getJSONObject("answer")), R.color.surface, null);
                button("Confirm this answer", () -> model.confirm(id), "live-confirm-" + i)
                        .setEnabled(!model.busy && model.pendingTurn == null && !model.generationUncertain);
            }
        }
        JSONObject ready = session.getJSONObject("generationReadiness");
        if (ready.getBoolean("ready")) {
            ui.text("Review your trip", 22, true);
            for (String id : LivePlanningProtocol.STEPS)
                ui.card(LivePlanningProtocol.TITLES[LivePlanningProtocol.index(id)],
                        LivePlanningProtocol.answer(id, steps.getJSONObject(id).getJSONObject("answer")), R.color.surface, null);
            if (model.savedTripId.isEmpty() && !model.generationUncertain)
                button("Generate and save itinerary", () -> new MaterialAlertDialogBuilder(this)
                        .setTitle("Generate this trip?").setMessage("The backend will research real flight, hotel and place options. This may take several minutes and uses configured model/provider quota. Prices are partial quotes; no bookings are made.")
                        .setNegativeButton("Review", null).setPositiveButton("Generate", (d, w) -> model.generate()).show(), "live-generate");
        } else {
            JSONArray blocking = ready.getJSONArray("blockingFields");
            if (model.selected == 7) {
                java.util.ArrayList<String> reasons = new java.util.ArrayList<>();
                for (int i = 0; i < blocking.length(); i++) {
                    String reason = blocking.getString(i);
                    try { reason = LivePlanningProtocol.TITLES[LivePlanningProtocol.index(reason)]; }
                    catch (IllegalArgumentException message) { /* Human-readable backend limitation. */ }
                    reasons.add(reason);
                }
                ui.text("Before generation: " + android.text.TextUtils.join(" · ", reasons), 14, false);
            }
        }
        button("Review an earlier step", () -> {
            java.util.ArrayList<String> labels = new java.util.ArrayList<>(); java.util.ArrayList<Integer> indices = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                String state = steps.optJSONObject(LivePlanningProtocol.STEPS[i]).optString("state");
                if (state.equals("LOCKED")) break;
                labels.add(LivePlanningProtocol.TITLES[i]); indices.add(i);
                if (!state.equals("CONFIRMED")) break;
            }
            new MaterialAlertDialogBuilder(this).setTitle("Review planning step")
                    .setItems(labels.toArray(new String[0]), (d, which) -> model.select(indices.get(which))).show();
        }, "live-review");
    }
    private MaterialButton button(String text, Runnable click, String tag) {
        MaterialButton button = ui.button(R.string.dialog_ok, click); button.setText(text); button.setTag(tag);
        button.setEnabled(!model.busy); return button;
    }
    private void chooseDates() {
        java.util.Calendar start = java.util.Calendar.getInstance(); start.add(java.util.Calendar.DAY_OF_MONTH, 30);
        new android.app.DatePickerDialog(this, (v, year, month, day) -> {
            int first = year * 10000 + (month + 1) * 100 + day;
            java.util.Calendar suggested = TripDates.calendar(first); suggested.add(java.util.Calendar.DAY_OF_MONTH, 3);
            new android.app.DatePickerDialog(this, (view, ey, em, ed) -> {
                int last = ey * 10000 + (em + 1) * 100 + ed;
                int duration = TripDates.daysBetweenInclusive(first, last);
                if (duration < 2 || duration > 60) {
                    new MaterialAlertDialogBuilder(this).setMessage("Choose an overnight trip of at most 60 calendar days.").setPositiveButton("OK", null).show(); return;
                }
                String from = String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month + 1, day);
                String to = String.format(java.util.Locale.US, "%04d-%02d-%02d", ey, em + 1, ed);
                String message = "Fixed dates: depart " + from + " and leave the destination on " + to + ".";
                model.draft(message); input.setText(message);
            }, suggested.get(java.util.Calendar.YEAR), suggested.get(java.util.Calendar.MONTH), suggested.get(java.util.Calendar.DAY_OF_MONTH)).show();
        }, start.get(java.util.Calendar.YEAR), start.get(java.util.Calendar.MONTH), start.get(java.util.Calendar.DAY_OF_MONTH)).show();
    }
}
