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
    static final String EXTRA_WELCOME_REMAINING = "welcome_remaining";
    private LaunchWelcome launchWelcome;
    public static final String EXTRA_EDIT_PROFILE = "edit_profile";
    private final ExecutorService storage = Executors.newSingleThreadExecutor();
    private TextInputEditText birthDate, location, name, nationality, gender;
    // 已有自由文本偏好仅保留在存档中；新界面只通过标签表达偏好。
    private String legacyPreferences = "";
    private boolean restoringInterests;
    private static final String[] GENDERS = {"Prefer not to tell", "Woman", "Man", "Non-binary", "Self-described"};
    private com.google.android.material.chip.ChipGroup tags, relatedTags;
    private final java.util.Map<String, com.google.android.material.chip.Chip> interestChips = new java.util.LinkedHashMap<>();
    private RegionCatalog regions;
    private RegionCatalog.Country locationCountry;
    private boolean needsRegion;
    private TextInputLayout locationBox;
    private CurrentLocation currentLocation;
    private boolean editing, readFailed, opening;
    private org.json.JSONObject restoredDraft;
    private final java.util.List<android.view.View> controls = new java.util.ArrayList<>();
    private MaterialButton submit, previous;
    private LinearLayout personalPage, interestsPage;
    private TextView stepLabel;
    private int profileStep;
    private Integer legacyAge;
    private static final String PRIVATE = "Prefer not to tell";
    private TextView status, locationStatus;
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
        regions = new RegionCatalog(getApplicationContext());
        currentLocation = new CurrentLocation(this, new CurrentLocation.Listener() {
            @Override public void status(String text) { setLocationStatus(text); }
            @Override public void found(String name) { locationCountry = null; needsRegion = false; location.setText(name); setLocationStatus("Location updated."); }
        }, state != null && state.getBoolean("location_permission_pending"));
        editing = getIntent().getBooleanExtra(EXTRA_EDIT_PROFILE, false);
        if (state != null && state.containsKey("profile_draft")) {
            try { restoredDraft = new org.json.JSONObject(state.getString("profile_draft")); }
            catch (org.json.JSONException ignored) { /* 系统草稿异常时仍可重读本机资料。 */ }
        }
        profileStep = state == null ? 0 : state.getInt("profile_step", 0);
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (profileStep == 1) showStep(0); else if (editing) finish(); else finishAffinity();
            }
        });
        ui.card(getString(R.string.registration_brand), getString(R.string.registration_welcome), R.color.lime, null);
        ui.title(R.string.registration_title, R.string.registration_subtitle);
        stepLabel = ui.text("Step 1 of 2 · About you", 14, true);
        ui.text("Every field is optional. Share only what you are comfortable with.", 14, false);
        HomeUi outerUi = ui;
        personalPage = new LinearLayout(this); personalPage.setOrientation(LinearLayout.VERTICAL);
        outerUi.content.addView(personalPage);
        ui = new HomeUi(personalPage);
        name = field(ui, R.id.registrationName, R.string.profile_name, InputType.TYPE_CLASS_TEXT, 200);
        privateAction(ui, name, () -> name.setText(PRIVATE));
        gender = field(ui, R.id.registrationGender, R.string.profile_gender, InputType.TYPE_CLASS_TEXT, 200);
        gender.setHint("Select gender (optional)");
        selectionField(gender, () -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Odyssey_ProfilePicker)
                .setTitle("Gender").setItems(GENDERS, (dialog, which) -> gender.setText(GENDERS[which]))
                .setNegativeButton("Cancel", null).show());
        birthDate = field(ui, R.id.registrationBirthDate, R.string.profile_birth_date, InputType.TYPE_CLASS_TEXT, 30);
        birthDate.setHint("Select date (optional)");
        selectionField(birthDate, () -> BirthDatePicker.show(this, value(birthDate), date -> {
            legacyAge = null;
            birthDate.setText(date.isEmpty() ? PRIVATE : date);
        }));
        nationality = field(ui, R.id.registrationNationality, R.string.profile_nationality, InputType.TYPE_CLASS_TEXT, 200);
        nationality.setHint("Select nationality (optional)");
        selectionField(nationality, () -> chooseCountry(true));
        // 第一页与偏好页各自保留控件，前后切换不会清空输入或已选标签。
        interestsPage = new LinearLayout(this); interestsPage.setOrientation(LinearLayout.VERTICAL);
        outerUi.content.addView(interestsPage);
        ui = new HomeUi(interestsPage);
        location = field(ui, R.id.registrationLocation, R.string.registration_location,
                InputType.TYPE_CLASS_TEXT, 200);
        location.setHint("Choose location (optional)");
        // 框内文字打开国家→地区选择，右侧独立图标只负责主动定位。
        location.setFocusable(false); location.setShowSoftInputOnFocus(false);
        location.setOnClickListener(view -> { if (needsRegion) chooseRegion(); else chooseCountry(false); });
        locationBox = (TextInputLayout) location.getParent().getParent();
        locationBox.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        locationBox.setEndIconDrawable(R.drawable.ic_my_location);
        locationBox.setEndIconCheckable(false);
        locationBox.setEndIconContentDescription(R.string.profile_use_location);
        locationBox.setEndIconTintList(android.content.res.ColorStateList.valueOf(ui.color(R.color.profile_field_text)));
        locationBox.setEndIconOnClickListener(view -> { if (location.isEnabled()) currentLocation.start(); });
        privateAction(ui, location, () -> { currentLocation.cancel(); needsRegion = false; locationCountry = null; location.setText(PRIVATE); setLocationStatus(""); });
        locationStatus = ui.text("", 13, false);
        locationStatus.setVisibility(android.view.View.GONE);
        locationStatus.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        ui.text("Travel interests · tap to explore", 16, true);
        tags = new com.google.android.material.chip.ChipGroup(this);
        tags.setSingleSelection(false);
        tags.setChipSpacingHorizontal(ui.dp(2)); tags.setChipSpacingVertical(0);
        // 先放全部根标签，再放衍生标签：展开时始终出现在初始标签的下方。
        for (String root : InterestGroups.all().keySet()) addInterest(ui, root, tags);
        ui.content.addView(tags);
        // 单独一组保证相关选项从下一行开始，不挤进初始标签的最后一行。
        relatedTags = new com.google.android.material.chip.ChipGroup(this);
        relatedTags.setChipSpacingHorizontal(ui.dp(2)); relatedTags.setChipSpacingVertical(0);
        for (String[] children : InterestGroups.all().values())
            for (String child : children) addInterest(ui, child, relatedTags);
        ui.content.addView(relatedTags);
        updateInterestVisibility();
        ui = outerUi;
        ui.text(getString(R.string.registration_privacy), 12, false);
        status = ui.text("", 14, false);
        status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        previous = ui.button(R.string.profile_back, () -> showStep(0));
        previous.setId(R.id.registrationPrevious); controls.add(previous);
        submit = ui.button(R.string.profile_next, () -> {
            if (readFailed) loadProfile();
            else if (profileStep == 0) showStep(1);
            else save();
        });
        submit.setId(R.id.registrationNext);
        showStep(profileStep);
        if (editing) {
            MaterialButton cancel = ui.button(R.string.profile_cancel, this::finish);
            cancel.setId(R.id.registrationCancel); controls.add(cancel);
        }
        // 首次启动接续首页的票券；从个人页编辑资料不播放欢迎动画。
        launchWelcome = new LaunchWelcome(this, state, R.id.registrationRoot,
                getIntent().getLongExtra(EXTRA_WELCOME_REMAINING, 0));
        loadProfile();
    }
    /** 两页只切换可见性；完成按钮与“下一步”分开，不提前写入个人资料。 */
    private void showStep(int step) {
        profileStep = step == 1 ? 1 : 0;
        personalPage.setVisibility(profileStep == 0 ? android.view.View.VISIBLE : android.view.View.GONE);
        interestsPage.setVisibility(profileStep == 1 ? android.view.View.VISIBLE : android.view.View.GONE);
        stepLabel.setText(profileStep == 0 ? "Step 1 of 2 · About you" : "Step 2 of 2 · Your travel style");
        previous.setVisibility(profileStep == 1 ? android.view.View.VISIBLE : android.view.View.GONE);
        submit.setText(profileStep == 0 ? R.string.profile_next : (editing ? R.string.profile_save : R.string.registration_continue));
        if (status != null) status.setText("");
        android.view.View focus = getCurrentFocus();
        if (focus != null) {
            ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(), 0);
            focus.clearFocus();
        }
        androidx.core.widget.NestedScrollView scroll = findViewById(R.id.registrationRoot);
        scroll.scrollTo(0, 0);
    }
    /** 不透露与留空在存档中都表示未提供，避免把提示词当作真实姓名或国籍。 */
    private String disclosed(TextInputEditText input) { return PRIVATE.equals(value(input)) ? "" : value(input); }
    private void privateAction(HomeUi ui, TextInputEditText input, Runnable action) {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(PRIVATE); button.setAllCaps(false);
        button.setTextColor(ui.color(R.color.text_secondary));
        button.setContentDescription(input.getContentDescription() + ": " + PRIVATE);
        button.setOnClickListener(view -> action.run()); controls.add(button);
        ui.content.addView(button);
    }
    /** 选择框仍使用同一白底输入样式，但禁止唤出键盘。 */
    private void selectionField(TextInputEditText input, Runnable action) {
        input.setFocusable(false); input.setShowSoftInputOnFocus(false);
        input.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.arrow_down_float, 0);
        input.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.profile_field_icon)));
        input.setOnClickListener(view -> action.run());
    }
    private void addInterest(HomeUi ui, String id, com.google.android.material.chip.ChipGroup container) {
        com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(this);
        chip.setId(android.view.View.generateViewId()); chip.setText(PreferenceTags.ALL.get(id)); chip.setTag(id);
        chip.setCheckable(true); chip.setEnsureMinTouchTargetSize(true);
        chip.setCheckedIconVisible(false); chip.setChipIconVisible(false);
        chip.setTextSize(13); chip.setChipMinHeight(ui.dp(44));
        chip.setChipStartPadding(ui.dp(8)); chip.setChipEndPadding(ui.dp(8));
        chip.setTextStartPadding(ui.dp(2)); chip.setTextEndPadding(ui.dp(2));
        // 选中只加粗标题，不添加勾选图标或改变背景色。
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(ui.color(R.color.surface)));
        chip.setTextColor(ui.color(R.color.text_primary));
        chip.setTypeface(null, android.graphics.Typeface.NORMAL);
        chip.setShapeAppearanceModel(chip.getShapeAppearanceModel().toBuilder().setAllCornerSizes(ui.dp(24)).build());
        chip.setChipStrokeWidth(ui.dp(1));
        chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(ui.color(R.color.outline)));
        chip.setOnCheckedChangeListener((button, checked) -> {
            chip.setTypeface(null, checked ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            if (!restoringInterests) updateInterestVisibility();
        });
        interestChips.put(id, chip); container.addView(chip); controls.add(chip);
    }
    /** 已选择的子项始终可见，编辑旧资料时不会把用户的选择藏起来。 */
    private void updateInterestVisibility() {
        int appearing = 0;
        boolean animate = !restoringInterests && tags.isLaidOut()
                && (android.os.Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled());
        for (java.util.Map.Entry<String, String[]> group : InterestGroups.all().entrySet()) {
            com.google.android.material.chip.Chip parent = interestChips.get(group.getKey());
            if (parent == null) continue;
            boolean expanded = parent.isChecked();
            for (String id : group.getValue()) {
                com.google.android.material.chip.Chip child = interestChips.get(id);
                if (child != null && child.isChecked()) expanded = true;
            }
            for (String id : group.getValue()) {
                com.google.android.material.chip.Chip child = interestChips.get(id);
                if (child == null) continue;
                int visibility = expanded ? android.view.View.VISIBLE : android.view.View.GONE;
                if (child.getVisibility() == visibility) continue;
                child.animate().cancel();
                child.setVisibility(visibility); child.setAlpha(1f); child.setTranslationY(0f);
                if (expanded && animate) {
                    // 逐项淡入并轻微上移；系统关闭动画时直接显示，避免不可操作的透明项。
                    child.setAlpha(0f); child.setTranslationY(8 * getResources().getDisplayMetrics().density);
                    child.animate().alpha(1f).translationY(0f).setDuration(400)
                            .setStartDelay(Math.min(appearing++, 8) * 25L)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
                }
            }
        }
    }
    private void chooseCountry(boolean forNationality) {
        java.util.List<RegionCatalog.Country> countries = regions.countries();
        String[] names = new String[countries.size()];
        for (int i = 0; i < countries.size(); i++) names[i] = countries.get(i).name;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Odyssey_ProfilePicker)
                .setTitle(forNationality ? "Select nationality" : "Select country")
                .setItems(names, (dialog, which) -> {
                    RegionCatalog.Country selected = countries.get(which);
                    if (forNationality) nationality.setText(selected.name);
                    else {
                        currentLocation.cancel(); locationCountry = selected; location.setText(selected.name);
                        needsRegion = !regions.regions(selected.code).isEmpty();
                        if (needsRegion) chooseRegion();
                        else setLocationStatus("No regional subdivisions are listed for this country.");
                    }
                }).setNeutralButton(PRIVATE, (dialog, which) -> {
                    if (forNationality) nationality.setText(PRIVATE);
                    else { currentLocation.cancel(); locationCountry = null; needsRegion = false; location.setText(PRIVATE); setLocationStatus(""); }
                }).setNegativeButton("Cancel", null).show();
    }
    private void chooseRegion() {
        if (locationCountry == null) { setLocationStatus("Choose a country first."); return; }
        java.util.List<String> choices = regions.regions(locationCountry.code);
        if (choices.isEmpty()) { setLocationStatus("No regional subdivisions are listed for this country."); return; }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Odyssey_ProfilePicker)
                .setTitle("Select state or region")
                .setItems(choices.toArray(new String[0]), (dialog, which) -> {
                    currentLocation.cancel(); needsRegion = false; location.setText(choices.get(which) + ", " + locationCountry.name);
                    setLocationStatus("");
                }).setNegativeButton("Cancel", null).show();
    }
    private void setLocationStatus(String message) {
        locationStatus.setText(message);
        locationStatus.setVisibility(message.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
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
                    showStep(profileStep);
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
        birthDate.setText(profile.optString("birth_date", ""));
        legacyAge = profile.opt("age") instanceof Integer ? Integer.valueOf(profile.optInt("age")) :
                (profile.opt("legacy_age") instanceof Integer ? Integer.valueOf(profile.optInt("legacy_age")) : null);
        location.setText(profile.optString("residential_location", ""));
        String countryCode = profile.optString("location_country_code", "");
        needsRegion = profile.optBoolean("location_needs_region", false);
        for (RegionCatalog.Country country : regions.countries())
            if (country.code.equals(countryCode) || value(location).endsWith(", " + country.name)
                    || value(location).equals(country.name)) { locationCountry = country; break; }
        legacyPreferences = profile.optString("travel_preferences", "");
        String savedGender = profile.optString("gender", "");
        if ("Prefer not to say".equals(savedGender)) savedGender = PRIVATE;
        gender.setText(savedGender);
        restoringInterests = true;
        org.json.JSONArray selected = profile.optJSONArray("preference_tags");
        for (com.google.android.material.chip.Chip chip : interestChips.values()) {
            boolean checked = false;
            if (selected != null) for (int j = 0; j < selected.length(); j++)
                if (chip.getTag().equals(selected.optString(j))) checked = true;
            chip.setChecked(checked);
        }
        updateInterestVisibility();
        restoringInterests = false;
    }
    private TextInputEditText field(HomeUi ui, int id, int hint, int type, int limit) {
        // 标题放在白色输入框之外，浅/深色背景都使用对应正文色，避免浮动标签跨越深色背景时看不清。
        TextView label = ui.text(getString(hint), 14, true);
        label.setPadding(0, ui.dp(2), 0, ui.dp(4));
        TextInputLayout wrapper = new TextInputLayout(this);
        wrapper.setHintEnabled(false);
        wrapper.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        wrapper.setBoxBackgroundColor(android.graphics.Color.WHITE);
        // 品牌 lime 适合按钮填充，但在米白底上不足以作为小号标签文字；使用语义正文色。
        android.content.res.ColorStateList labelColor = android.content.res.ColorStateList.valueOf(android.graphics.Color.rgb(33, 31, 29));
        wrapper.setHintTextColor(labelColor);
        wrapper.setDefaultHintTextColor(labelColor);
        wrapper.setBoxStrokeColor(ui.color(R.color.text_primary));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = ui.dp(16);
        ui.content.addView(wrapper, params);
        TextInputEditText input = new TextInputEditText(wrapper.getContext());
        input.setId(id); input.setInputType(type);
        input.setContentDescription(getString(hint));
        input.setTextColor(android.graphics.Color.rgb(33, 31, 29));
        input.setHintTextColor(getColor(R.color.profile_field_icon));
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});
        input.setSingleLine(true);
        wrapper.addView(input, new LinearLayout.LayoutParams(-1, -2));
        controls.add(input);
        return input;
    }
    private String value(TextInputEditText input) {
        return InputValidation.text(input.getText() == null ? null : input.getText().toString());
    }
    private void save() {
        // 保存时停止旧定位，避免异步回包在手动选择之后改写显示内容。
        currentLocation.cancel();
        final org.json.JSONObject profile;
        try {
            // 国家本身也可作为当前位置；地区和个人字段均可跳过。
            org.json.JSONArray selected = new org.json.JSONArray();
            for (com.google.android.material.chip.Chip chip : interestChips.values()) {
                if (chip.isChecked()) selected.put(chip.getTag());
            }
            profile = new org.json.JSONObject().put("schema_version", 3).put("onboarding_complete", true)
                    .put("name", InputValidation.optionalText(disclosed(name), 200, "Name"))
                    .put("gender", disclosed(gender)).put("birth_date", InputValidation.birthDate(disclosed(birthDate)))
                    .put("nationality", InputValidation.optionalText(disclosed(nationality), 200, "Nationality"))
                    .put("residential_location", InputValidation.optionalText(disclosed(location), 200, "Residential location"))
                    .put("travel_preferences", InputValidation.optionalText(legacyPreferences, 2000, "Preferences"))
                    .put("preference_tags", selected);
            if (legacyAge != null) profile.put("legacy_age", legacyAge);
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
        locationBox.setEnabled(!busy);
        for (android.view.View control : controls) control.setEnabled(!busy);
    }
    /** 旋转只保存未提交草稿，动态标签使用稳定业务 ID 而非生成的 View ID。 */
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("profile_step", profileStep);
        if (launchWelcome != null) launchWelcome.save(state);
        state.putBoolean("location_permission_pending", currentLocation.isWaitingForPermission());
        // 连续旋转时磁盘读取可能尚未完成，此时继续传递已有草稿，不能让旧存档取代它。
        if (restoredDraft != null) state.putString("profile_draft", restoredDraft.toString());
        if (name.isEnabled()) {
            try {
                org.json.JSONArray selected = new org.json.JSONArray();
                for (com.google.android.material.chip.Chip chip : interestChips.values()) {
                    if (chip.isChecked()) selected.put(chip.getTag());
                }
                state.putString("profile_draft", new org.json.JSONObject().put("name", value(name))
                        .put("nationality", value(nationality)).put("birth_date", value(birthDate)).put("legacy_age", legacyAge)
                        .put("gender", value(gender)).put("residential_location", value(location))
                        .put("location_country_code", locationCountry == null ? "" : locationCountry.code)
                        .put("location_needs_region", needsRegion)
                        .put("travel_preferences", legacyPreferences).put("preference_tags", selected).toString());
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
    @Override protected void onResume() {
        super.onResume();
        if (launchWelcome != null) launchWelcome.resume();
    }
    @Override protected void onPause() {
        if (launchWelcome != null) launchWelcome.pause();
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (launchWelcome != null) launchWelcome.dismiss();
        currentLocation.close(); storage.shutdown(); super.onDestroy();
    }
}
