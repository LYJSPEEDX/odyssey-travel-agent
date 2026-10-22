package com.example.odyssey;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Startup gate: main app opens only after successful profile persistence. */
public class RegistrationActivity extends AppCompatActivity {
    private final ExecutorService storage = Executors.newSingleThreadExecutor();
    private TextInputEditText age, location, preferences;
    private MaterialButton submit;
    private TextView status;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_registration);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.registrationRoot), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        HomeUi ui = new HomeUi(findViewById(R.id.registrationContent));
        ui.card(getString(R.string.registration_brand), getString(R.string.registration_welcome), R.color.lime, null);
        ui.title(R.string.registration_title, R.string.registration_subtitle);
        age = field(ui, R.id.registrationAge, R.string.registration_age, InputType.TYPE_CLASS_NUMBER, 3);
        location = field(ui, R.id.registrationLocation, R.string.registration_location,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, 200);
        preferences = field(ui, R.id.registrationPreferences, R.string.registration_preferences,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 2000);
        ui.text(getString(R.string.registration_privacy), 12, false);
        status = ui.text("", 14, false);
        status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        submit = ui.button(R.string.registration_continue, this::save);
        busy(true);
        storage.execute(() -> {
            try {
                boolean exists = new ProfileStore(this).load() != null;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (exists) openHome(); else busy(false);
                });
            } catch (java.io.IOException | org.json.JSONException exception) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    busy(false); status.setText(R.string.registration_read_error);
                });
            }
        });
    }
    private TextInputEditText field(HomeUi ui, int id, int hint, int type, int limit) {
        TextInputLayout wrapper = new TextInputLayout(this);
        wrapper.setHint(hint);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = ui.dp(16);
        ui.content.addView(wrapper, params);
        TextInputEditText input = new TextInputEditText(wrapper.getContext());
        input.setId(id); input.setInputType(type);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});
        input.setSingleLine(id != R.id.registrationPreferences);
        if (id == R.id.registrationPreferences) input.setMinLines(3);
        wrapper.addView(input, new LinearLayout.LayoutParams(-1, -2));
        return input;
    }
    private String value(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
    private void save() {
        int years;
        try { years = Integer.parseInt(value(age)); }
        catch (NumberFormatException exception) { years = 0; }
        if (years < 1 || years > 120) {
            age.setError(getString(R.string.registration_invalid_age)); age.requestFocus(); return;
        }
        String residence = value(location), interests = value(preferences);
        if (residence.isEmpty()) {
            location.setError(getString(R.string.registration_invalid_location)); location.requestFocus(); return;
        }
        final int validAge = years;
        busy(true); status.setText(R.string.registration_saving);
        storage.execute(() -> {
            try {
                new ProfileStore(this).save(validAge, residence, interests);
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) openHome(); });
            } catch (java.io.IOException | org.json.JSONException exception) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    busy(false); status.setText(R.string.registration_save_error);
                });
            }
        });
    }
    private void busy(boolean busy) {
        submit.setEnabled(!busy);
        age.setEnabled(!busy); location.setEnabled(!busy); preferences.setEnabled(!busy);
    }
    private void openHome() { startActivity(new Intent(this, HomeActivity.class)); finish(); }
    @Override protected void onDestroy() { storage.shutdown(); super.onDestroy(); }
}
