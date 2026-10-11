package com.example.odyssey;

import android.os.Bundle;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.os.BundleCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;
import com.google.android.material.button.MaterialButton;
import java.util.Calendar;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements PlannerFormRenderer.Actions {
    private TripPlanState state = new TripPlanState();
    private Calendar calendarMonth = Calendar.getInstance();
    private boolean editingDeparture = true;
    private PlannerFormRenderer form;
    private AppCompatEditText input;
    private TextView feedback;
    private NestedScrollView scroll;
    private OnBackPressedCallback backCallback;
    private String generationId;
    private int saveStatus; // 0: saving, 1: saved, 2: failed
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable finishMock = () -> {
        if (state.isGenerating()) {
            state.finishGeneration();
            render(true);
            saveTrip();
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            TripPlanState restored = BundleCompat.getSerializable(savedInstanceState,
                    "trip_state", TripPlanState.class);
            if (restored != null) state = restored;
            generationId = savedInstanceState.getString("generation_id");
            saveStatus = savedInstanceState.getInt("trip_save_status", 0);
            editingDeparture = savedInstanceState.getBoolean("editing_departure", state.getDeparture().isEmpty());
            int month = savedInstanceState.getInt("calendar_month", TripDates.key(calendarMonth));
            calendarMonth = TripDates.calendar(month);
        }
        calendarMonth.set(Calendar.DAY_OF_MONTH, 1);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        scroll = findViewById(R.id.main);
        input = findViewById(R.id.messageInput);
        feedback = findViewById(R.id.inputFeedback);
        form = new PlannerFormRenderer(findViewById(R.id.stepContent), state, calendarMonth, this);
        findViewById(R.id.backButton).setOnClickListener(view -> back());
        findViewById(R.id.nextButton).setOnClickListener(view -> next());
        findViewById(R.id.sendButton).setOnClickListener(view -> submitMessage());
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEND) return false;
            submitMessage();
            return true;
        });
        backCallback = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { back(); }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);
        ViewCompat.setOnApplyWindowInsetsListener(scroll, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        render(false);
        if (state.isGenerated() && saveStatus != 1) saveTrip();
    }

    private void render(boolean scrollToTop) {
        boolean generation = state.isGenerated() || state.isGenerating();
        int step = state.getCurrentStep();
        String[] titles = getResources().getStringArray(R.array.planner_headlines);
        String[] prompts = getResources().getStringArray(R.array.planner_prompts);
        String[] help = getResources().getStringArray(R.array.planner_help);
        text(R.id.planHeadline, generation ? getString(state.isGenerating()
                ? R.string.generation_loading_title : R.string.generation_done_title) : titles[step - 1]);
        text(R.id.assistantTitle, generation ? getString(state.isGenerating()
                ? R.string.generation_loading_prompt : R.string.generation_done_prompt)
                : step == 1 && !editingDeparture ? getString(R.string.choose_destination_prompt) : prompts[step - 1]);
        text(R.id.assistantMessage, generation ? getString(state.isGenerating()
                ? R.string.generation_loading_help : R.string.generation_done_help) : help[step - 1]);
        text(R.id.stepText, generation ? getString(R.string.demo_badge)
                : getString(R.string.step_count, step, TripPlanState.TOTAL_STEPS));
        int[] progressIds = {R.id.progressStep1, R.id.progressStep2, R.id.progressStep3,
                R.id.progressStep4, R.id.progressStep5};
        for (int i = 0; i < progressIds.length; i++) {
            findViewById(progressIds[i]).setBackgroundColor(ContextCompat.getColor(this,
                    i < step ? R.color.lime : R.color.outline));
        }
        findViewById(R.id.tripBoard).setVisibility(state.getDeparture().isEmpty() ? View.GONE : View.VISIBLE);
        text(R.id.tripBoardDestination, state.getDestination().isEmpty()
                ? getString(R.string.depart_from, state.getDeparture())
                : getString(R.string.trip_route, state.getDeparture(), state.getDestination()));
        String status = state.getDestination().isEmpty() ? getString(R.string.departure_confirmed)
                : step == 1 ? getString(R.string.location_confirmed)
                : state.isStepComplete(2) ? form.dateSummary() : getString(R.string.date_start_prompt);
        text(R.id.tripBoardStatus, status);
        MaterialButton next = findViewById(R.id.nextButton);
        next.setEnabled(state.canGoNext());
        next.setVisibility(generation ? View.INVISIBLE : View.VISIBLE);
        next.setContentDescription(getString(step == 5 ? R.string.generate_action : R.string.navigation_next));
        findViewById(R.id.backButton).setEnabled(true);
        findViewById(R.id.backButton).setContentDescription(getString(
                state.canGoBack() ? R.string.navigation_back : R.string.back_home));
        backCallback.setEnabled(state.canGoBack());
        form.render();
        findViewById(R.id.composerContainer).setVisibility(generation ? View.GONE : View.VISIBLE);
        input.setError(null);
        input.setHint(step == 1 && !editingDeparture ? getString(R.string.destination_hint)
                : getResources().getStringArray(R.array.planner_hints)[step - 1]);
        input.setText(state.getDraft(step));
        input.setSelection(input.length());
        feedback.setVisibility(View.GONE);
        if (scrollToTop) scroll.post(() -> scroll.smoothScrollTo(0, 0));
        if (state.isGenerating()) scheduleMock();
    }

    @Override public void changed() {
        saveDraft();
        render(false);
    }

    @Override public void chooseDeparture(String city) {
        state.selectDeparture(city);
        editingDeparture = false;
        input.setText("");
        state.setDraft(1, "");
        render(false);
    }

    @Override public void editLocation(boolean departure) {
        saveDraft();
        editingDeparture = departure;
        render(false);
        input.requestFocus();
        input.post(() -> {
            scroll.smoothScrollTo(0, input.getBottom() + findViewById(R.id.composerContainer).getTop());
            new WindowInsetsControllerCompat(getWindow(), input).show(WindowInsetsCompat.Type.ime());
        });
    }

    @Override public void dateSelected(int date) {
        boolean reset = state.getEndDate() == 0 && state.getStartDate() != 0 && date < state.getStartDate();
        state.selectDate(date);
        changed();
        if (reset) showFeedback(R.string.date_restarted);
    }

    private void submitMessage() {
        String message = input.getText() == null ? "" : input.getText().toString().trim();
        if (message.isEmpty()) { input.setError(getString(R.string.empty_message)); return; }
        int step = state.getCurrentStep();
        if (step == 1) {
            if (editingDeparture) {
                state.selectDeparture(message);
                editingDeparture = false;
            } else {
                if (message.equalsIgnoreCase(state.getDeparture())) {
                    input.setError(getString(R.string.same_city_error));
                    return;
                }
                state.selectDestination(message);
            }
        } else if (step == 3) state.setCompanion(message);
        else if (step == 4) state.setBudget(message);
        else state.setNote(step, message);
        state.setDraft(step, "");
        input.setText("");
        hideKeyboard();
        render(false);
        if (step > 1) showFeedback(step == 2 ? R.string.date_notes_saved : R.string.notes_saved);
        if (step == 1 && state.getDeparture().equalsIgnoreCase(state.getDestination())) {
            showFeedback(R.string.same_city_error);
        }
    }

    private void next() {
        // Never silently discard a typed answer when the user presses next.
        if (input.length() > 0) submitMessage();
        if (!state.canGoNext() || input.getError() != null) return;
        hideKeyboard();
        state.goNext();
        if (state.isGenerating()) {
            generationId = UUID.randomUUID().toString();
            saveStatus = 0;
        }
        render(true);
    }

    @Override public void back() {
        if (!state.canGoBack()) { finish(); return; }
        saveDraft();
        hideKeyboard();
        handler.removeCallbacks(finishMock);
        state.goBack();
        render(true);
    }

    private void saveDraft() {
        if (!state.isGenerating() && !state.isGenerated())
            state.setDraft(state.getCurrentStep(), input.getText() == null ? "" : input.getText().toString());
    }
    private void hideKeyboard() {
        input.clearFocus();
        new WindowInsetsControllerCompat(getWindow(), input).hide(WindowInsetsCompat.Type.ime());
    }
    private void text(int id, String value) { ((TextView) findViewById(id)).setText(value); }
    private void showFeedback(int id) {
        feedback.setText(id);
        feedback.setVisibility(View.VISIBLE);
    }
    private void scheduleMock() {
        handler.removeCallbacks(finishMock);
        handler.postDelayed(finishMock, 1200); // Explicitly labelled demo transition, no API call.
    }
    @Override public int saveStatus() { return saveStatus; }
    @Override public void saveTrip() {
        if (!state.isGenerated()) return;
        if (generationId == null) generationId = UUID.randomUUID().toString();
        String savingId = generationId;
        SavedTrip snapshot;
        try { snapshot = SavedTrip.fromPlan(this, state, savingId); }
        catch (IllegalArgumentException error) { saveStatus = 2; render(false); return; }
        TripStore store = new TripStore(getApplicationContext());
        saveStatus = 0;
        render(false);
        saveExecutor.execute(() -> {
            boolean saved = store.save(snapshot);
            runOnUiThread(() -> {
                if (isDestroyed() || !savingId.equals(generationId) || !state.isGenerated()) return;
                saveStatus = saved ? 1 : 2;
                render(false);
            });
        });
    }
    @Override public void viewSavedTrips() {
        startActivity(new Intent(this, HomeActivity.class).putExtra(HomeActivity.EXTRA_OPEN_MY, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
    @Override public void viewItinerary() {
        if (saveStatus == 1 && generationId != null) startActivity(new Intent(this, TripDetailActivity.class)
                .putExtra(TripDetailActivity.EXTRA_ID, generationId));
    }
    @Override protected void onDestroy() {
        handler.removeCallbacks(finishMock);
        saveExecutor.shutdown(); // Already queued writes finish even if the page is closed.
        super.onDestroy();
    }
    @Override protected void onStart() {
        super.onStart();
        if (state.isGenerating()) scheduleMock();
    }
    @Override protected void onStop() {
        handler.removeCallbacks(finishMock);
        super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle outState) {
        saveDraft();
        outState.putSerializable("trip_state", state);
        outState.putString("generation_id", generationId);
        outState.putInt("trip_save_status", saveStatus);
        outState.putBoolean("editing_departure", editingDeparture);
        outState.putInt("calendar_month", TripDates.key(calendarMonth));
        super.onSaveInstanceState(outState);
    }
}
