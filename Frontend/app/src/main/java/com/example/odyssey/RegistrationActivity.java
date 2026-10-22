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
    public static final String EXTRA_EDIT_PROFILE = "edit_profile";
    private final ExecutorService storage = Executors.newSingleThreadExecutor();
    private TextInputEditText age, location, preferences, name, nationality;
    private android.widget.Spinner gender;
    private com.google.android.material.chip.ChipGroup tags;
    private boolean editing, readFailed, opening;
    private org.json.JSONObject restoredDraft;
    private final java.util.List<android.view.View> controls = new java.util.ArrayList<>();
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
        editing = getIntent().getBooleanExtra(EXTRA_EDIT_PROFILE, false);
        if (state != null && state.containsKey("profile_draft")) {
            try { restoredDraft = new org.json.JSONObject(state.getString("profile_draft")); }
            catch (org.json.JSONException ignored) { /* 系统草稿异常时仍可重读本机资料。 */ }
        }
        if (!editing) getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { finishAffinity(); }
        });
        ui.card(getString(R.string.registration_brand), getString(R.string.registration_welcome), R.color.lime, null);
        ui.title(R.string.registration_title, R.string.registration_subtitle);
        name = field(ui, R.id.registrationName, R.string.profile_name, InputType.TYPE_CLASS_TEXT, 200);
        ui.text("Gender", 14, true);
        gender = new android.widget.Spinner(this);
        gender.setId(R.id.registrationGender);
        gender.setAdapter(new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Prefer not to say", "Woman", "Man", "Non-binary", "Self-described"}));
        ui.content.addView(gender); controls.add(gender);
        age = field(ui, R.id.registrationAge, R.string.registration_age, InputType.TYPE_CLASS_NUMBER, 3);
        nationality = field(ui, R.id.registrationNationality, R.string.profile_nationality, InputType.TYPE_CLASS_TEXT, 200);
        location = field(ui, R.id.registrationLocation, R.string.registration_location,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, 200);
        preferences = field(ui, R.id.registrationPreferences, R.string.registration_preferences,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 2000);
        ui.text("Travel interests · choose any", 16, true);
        tags = new com.google.android.material.chip.ChipGroup(this);
        tags.setSingleSelection(false);
        for (java.util.Map.Entry<String, String> tag : PreferenceTags.ALL.entrySet()) {
            com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(this);
            chip.setId(android.view.View.generateViewId()); chip.setText(tag.getValue()); chip.setTag(tag.getKey());
            chip.setCheckable(true);
            chip.setShapeAppearanceModel(chip.getShapeAppearanceModel().toBuilder().setAllCornerSizes(ui.dp(24)).build());
            chip.setChipStrokeWidth(ui.dp(1));
            chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(ui.color(R.color.text_primary)));
            tags.addView(chip); controls.add(chip);
        }
        ui.content.addView(tags);
        ui.text(getString(R.string.registration_privacy), 12, false);
        status = ui.text("", 14, false);
        status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        submit = ui.button(editing ? R.string.profile_save : R.string.registration_continue, () -> {
            if (readFailed) loadProfile(); else save();
        });
        if (editing) {
            MaterialButton cancel = ui.button(R.string.profile_cancel, this::finish);
            cancel.setId(R.id.registrationCancel); controls.add(cancel);
        }
        loadProfile();
    }
    /** 读取失败时仅允许重试，不用空表单覆盖损坏的资料。 */
    private void loadProfile() {
        busy(true);
        storage.execute(() -> {
            try {
                org.json.JSONObject profile = new ProfileStore(this).load();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    readFailed = false;
                    submit.setText(editing ? R.string.profile_save : R.string.registration_continue);
                    if (!editing && ProfileStore.isComplete(profile)) { openHome(); return; }
                    if (restoredDraft != null) { prefill(restoredDraft); restoredDraft = null; }
                    else if (profile != null) prefill(profile);
                    busy(false); status.setText("");
                });
            } catch (java.io.IOException | org.json.JSONException | IllegalArgumentException exception) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    readFailed = true; busy(true); submit.setEnabled(true); submit.setText(R.string.retry);
                    status.setText(R.string.registration_read_error);
                });
            }
        });
    }
    /** 编辑副本只用于控件；取消操作不会写入 ProfileStore。 */
    private void prefill(org.json.JSONObject profile) {
        name.setText(profile.optString("name", "")); nationality.setText(profile.optString("nationality", ""));
        age.setText(profile.optString("age", "")); location.setText(profile.optString("residential_location", ""));
        preferences.setText(profile.optString("travel_preferences", ""));
        for (int i = 0; i < gender.getCount(); i++)
            if (gender.getItemAtPosition(i).equals(profile.optString("gender"))) gender.setSelection(i);
        org.json.JSONArray selected = profile.optJSONArray("preference_tags");
        for (int i = 0; i < tags.getChildCount(); i++) {
            com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) tags.getChildAt(i);
            boolean checked = false;
            if (selected != null) for (int j = 0; j < selected.length(); j++)
                if (chip.getTag().equals(selected.optString(j))) checked = true;
            chip.setChecked(checked);
        }
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
        controls.add(input);
        return input;
    }
    private String value(TextInputEditText input) {
        return InputValidation.text(input.getText() == null ? null : input.getText().toString());
    }
    private void save() {
        final org.json.JSONObject profile;
        try {
            org.json.JSONArray selected = new org.json.JSONArray();
            for (int i = 0; i < tags.getChildCount(); i++) {
                com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) tags.getChildAt(i);
                if (chip.isChecked()) selected.put(chip.getTag());
            }
            profile = new org.json.JSONObject().put("schema_version", 2)
                    .put("name", InputValidation.requiredText(value(name), 200, "Name"))
                    .put("gender", gender.getSelectedItem().toString()).put("age", InputValidation.age(value(age)))
                    .put("nationality", InputValidation.requiredText(value(nationality), 200, "Nationality"))
                    .put("residential_location", InputValidation.requiredText(value(location), 200, "Residential location"))
                    .put("travel_preferences", InputValidation.optionalText(value(preferences), 2000, "Preferences"))
                    .put("preference_tags", selected);
        } catch (IllegalArgumentException | org.json.JSONException exception) { status.setText(exception.getMessage()); return; }
        busy(true); status.setText(R.string.registration_saving);
        storage.execute(() -> {
            try {
                new ProfileStore(this).save(profile);
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) openHome(); });
            } catch (java.io.IOException | org.json.JSONException | IllegalArgumentException exception) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    busy(false); status.setText(R.string.registration_save_error);
                });
            }
        });
    }
    private void busy(boolean busy) {
        submit.setEnabled(!busy);
        for (android.view.View control : controls) control.setEnabled(!busy);
    }
    /** 旋转只保存未提交草稿，动态标签使用稳定业务 ID 而非生成的 View ID。 */
    @Override protected void onSaveInstanceState(Bundle state) {
        if (name.isEnabled()) {
            try {
                org.json.JSONArray selected = new org.json.JSONArray();
                for (int i = 0; i < tags.getChildCount(); i++) {
                    com.google.android.material.chip.Chip chip = (com.google.android.material.chip.Chip) tags.getChildAt(i);
                    if (chip.isChecked()) selected.put(chip.getTag());
                }
                state.putString("profile_draft", new org.json.JSONObject().put("name", value(name))
                        .put("nationality", value(nationality)).put("age", value(age))
                        .put("gender", gender.getSelectedItem().toString()).put("residential_location", value(location))
                        .put("travel_preferences", value(preferences)).put("preference_tags", selected).toString());
            } catch (org.json.JSONException ignored) { /* 文本和数组均来自本地控件。 */ }
        }
        super.onSaveInstanceState(state);
    }
    private void openHome() {
        if (opening) return;
        opening = true;
        if (!editing) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }
    @Override protected void onDestroy() { storage.shutdown(); super.onDestroy(); }
}
