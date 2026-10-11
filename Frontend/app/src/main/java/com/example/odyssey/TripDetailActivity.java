package com.example.odyssey;

import android.os.Bundle;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import org.json.JSONException;

public class TripDetailActivity extends AppCompatActivity {
    static final String EXTRA_ID = "trip_id";
    static final String EXTRA_SAMPLE = "sample";
    private int selectedDay;
    private ItineraryDocument document;
    private HomeUi ui;
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_trip_detail);
        MaterialToolbar toolbar = findViewById(R.id.tripDetailToolbar);
        toolbar.setNavigationOnClickListener(view -> finish());
        boolean sample = getIntent().getBooleanExtra(EXTRA_SAMPLE, false);
        toolbar.setTitle(sample ? R.string.sample_badge : R.string.my_title);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.tripDetailRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        ui = new HomeUi(findViewById(R.id.tripDetailContent));
        selectedDay = savedInstanceState == null ? 0 : savedInstanceState.getInt("itinerary_day", 0);
        try {
            String id = getIntent().getStringExtra(EXTRA_ID);
            SavedTrip trip = id == null ? null : sample ? ExampleTrips.get(this, id) : new TripStore(this).get(id);
            if (trip == null) {
                ui.text(getString(R.string.trip_not_found), 18, true);
                return;
            }
            if (trip.contractJson != null) {
                document = ItineraryDocument.parse(this, trip.contractJson);
                selectedDay = Math.max(0, Math.min(selectedDay, document.root.getJSONArray("days").length() - 1));
                renderItinerary();
                return;
            }
            ui.text(trip.route, 28, true);
            ui.text(trip.dates, 14, false);
            ui.card(getString(sample ? R.string.sample_badge : R.string.trip_saved),
                    getString(sample ? R.string.sample_description : R.string.saved_trip_notice),
                    R.color.mint, null);
            ui.text(trip.brief, 14, false);
            ui.text(trip.itinerary, 16, false);
        } catch (JSONException | ClassCastException exception) {
            ui.content.removeAllViews();
            ui.text(getString(R.string.trip_read_error), 16, false);
        }
    }
    private void renderItinerary() throws JSONException {
        ui.content.removeAllViews();
        new ItineraryRenderer(ui, document, selectedDay, index -> {
            selectedDay = index;
            try { renderItinerary(); }
            catch (JSONException error) {
                ui.content.removeAllViews();
                ui.text(getString(R.string.trip_read_error), 16, false);
            }
        }).render();
    }
    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("itinerary_day", selectedDay);
        super.onSaveInstanceState(outState);
    }
}
