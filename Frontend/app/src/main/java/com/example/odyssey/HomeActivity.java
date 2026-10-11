package com.example.odyssey;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.json.JSONException;
import java.util.List;

/** Three top-level destinations; planning remains a separate, focused five-step flow. */
public class HomeActivity extends AppCompatActivity {
    static final String EXTRA_OPEN_MY = "open_my";
    private BottomNavigationView navigation;
    private NestedScrollView scroll;
    private HomeUi ui;
    private int selected = R.id.nav_home;
    private int[] scrollPositions = {0, 0, 0};
    private OnBackPressedCallback backToHome;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        navigation = findViewById(R.id.bottomNavigation);
        scroll = findViewById(R.id.homeScroll);
        ui = new HomeUi(findViewById(R.id.homeContent));
        if (savedInstanceState != null) {
            selected = savedInstanceState.getInt("home_tab", R.id.nav_home);
            int[] positions = savedInstanceState.getIntArray("home_scroll");
            if (positions != null && positions.length == 3) scrollPositions = positions;
        } else if (getIntent().getBooleanExtra(EXTRA_OPEN_MY, false)) selected = R.id.nav_my;
        backToHome = new OnBackPressedCallback(selected != R.id.nav_home) {
            @Override public void handleOnBackPressed() { navigation.setSelectedItemId(R.id.nav_home); }
        };
        getOnBackPressedDispatcher().addCallback(this, backToHome);
        navigation.setOnItemSelectedListener(item -> {
            if (selected != item.getItemId()) scrollPositions[tabIndex(selected)] = scroll.getScrollY();
            selected = item.getItemId();
            render();
            int position = scrollPositions[tabIndex(selected)];
            scroll.post(() -> scroll.scrollTo(0, position));
            return true;
        });
        navigation.setSelectedItemId(selected);
        // BottomNavigationView handles its own bottom system inset.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.homeRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, 0);
            return insets;
        });
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_OPEN_MY, false)) navigation.setSelectedItemId(R.id.nav_my);
    }
    @Override protected void onResume() {
        super.onResume();
        // Returning from generation must refresh the library, not a cached empty state.
        if (ui != null) render();
    }
    private void render() {
        ui.content.removeAllViews();
        backToHome.setEnabled(selected != R.id.nav_home);
        if (selected == R.id.nav_my) myTrips();
        else if (selected == R.id.nav_settings) new HomeSettings(this, ui, this::render).render();
        else home();
    }
    private void home() {
        View banner = LayoutInflater.from(this).inflate(R.layout.home_banner, ui.content, false);
        banner.setOnClickListener(view -> startPlanner());
        ui.content.addView(banner);
        ui.title(R.string.home_inspiration_title, R.string.home_inspiration_subtitle);
        int[] colors = {R.color.mint, R.color.purple, R.color.red};
        List<SavedTrip> examples = ExampleTrips.all(this);
        for (int i = 0; i < examples.size(); i++) {
            SavedTrip trip = examples.get(i);
            ui.card(trip.route, getString(R.string.sample_badge) + "\n" + trip.dates,
                    colors[i % colors.length], () -> openTrip(trip.id, true)).setTag("example-" + trip.id);
        }
        ui.text(getString(R.string.sample_description), 12, false);
    }
    private void myTrips() {
        ui.title(R.string.my_title, R.string.my_subtitle);
        try {
            List<SavedTrip> trips = new TripStore(this).list();
            if (trips.isEmpty()) {
                ui.card(getString(R.string.my_empty_title), getString(R.string.my_empty_description),
                        R.color.surface, null).setId(R.id.myEmptyState);
                ui.button(R.string.create_my_trip, this::startPlanner);
            } else {
                for (SavedTrip trip : trips) {
                    ui.card(trip.route, getResources().getQuantityString(
                                    R.plurals.saved_trip_meta, trip.totalDays, trip.dates, trip.totalDays),
                            R.color.surface, () -> openTrip(trip.id, false)).setTag("saved-" + trip.id);
                }
            }
        } catch (JSONException | ClassCastException exception) {
            ui.text(getString(R.string.trip_read_error), 14, false);
            ui.button(R.string.retry, this::render);
        }
        ui.text(getString(R.string.local_only_notice), 12, false);
    }
    private void startPlanner() { startActivity(new Intent(this, MainActivity.class)); }
    private void openTrip(String id, boolean sample) {
        startActivity(new Intent(this, TripDetailActivity.class)
                .putExtra(TripDetailActivity.EXTRA_ID, id).putExtra(TripDetailActivity.EXTRA_SAMPLE, sample));
    }
    private int tabIndex(int id) { return id == R.id.nav_my ? 1 : id == R.id.nav_settings ? 2 : 0; }
    @Override protected void onSaveInstanceState(Bundle outState) {
        scrollPositions[tabIndex(selected)] = scroll.getScrollY();
        outState.putInt("home_tab", selected);
        outState.putIntArray("home_scroll", scrollPositions);
        super.onSaveInstanceState(outState);
    }
}
