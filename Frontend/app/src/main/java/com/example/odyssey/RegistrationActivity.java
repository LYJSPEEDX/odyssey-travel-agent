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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Startup gate: main app opens only after successful profile persistence. */
public class RegistrationActivity extends AppCompatActivity {
    static final String EXTRA_WELCOME_REMAINING = "welcome_remaining";
    private LaunchWelcome launchWelcome;
    public static final String EXTRA_EDIT_PROFILE = "edit_profile";
    private final ExecutorService storage = Executors.newSingleThreadExecutor();
    private TextInputEditText birthDate, location, name, nationality, gender, currency;
    // 已有自由文本偏好仅保留在存档中；新界面只通过标签表达偏好。
    private String legacyPreferences = "";
    private boolean restoringInterests;
    private static final String[] GENDERS = {"Prefer not to tell", "Woman", "Man", "Non-binary", "Self-described"};
    private com.google.android.material.chip.ChipGroup tags, relatedTags;
    private final java.util.Map<String, com.google.android.material.chip.Chip> interestChips = new java.util.LinkedHashMap<>();
    private RegionCatalog regions;
    private RegionCatalog.Country locationCountry;
    private boolean needsRegion;
    private android.widget.ImageView locationAction;
    private CurrentLocation currentLocation;
    private boolean editing, readFailed, opening;
    private org.json.JSONObject restoredDraft;
    private final java.util.List<android.view.View> controls = new java.util.ArrayList<>();
    private MaterialButton submit, previous;
    private LinearLayout personalPage, interestsPage;
    private TextView stepText;
    private int profileStep;
    private Integer legacyAge;
    private String residenceCountry = "", currencyOverride = "";
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
            @Override public void found(String name, String countryCode) {
                locationCountry = null; needsRegion = false; location.setText(name);
                try { residenceCountry = InputValidation.countryCode(countryCode); } catch (IllegalArgumentException ignored) { residenceCountry = ""; }
                refreshCurrency(); setLocationStatus("Location updated.");
            }
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
        // 黄框同时承担品牌与页面标题，不再单独放副标题。
        com.google.android.material.card.MaterialCardView hero = ui.card(getString(R.string.registration_brand), getString(R.string.registration_title), R.color.lime, null);
        TextView heroTitle = hero.findViewById(R.id.optionDescription);
        heroTitle.setTextSize(28); AppFonts.heading(heroTitle);
        ((LinearLayout.LayoutParams) hero.getLayoutParams()).bottomMargin = ui.dp(16);
        stepText = findViewById(R.id.registrationStepText);
        HomeUi outerUi = ui;
        personalPage = new LinearLayout(this); personalPage.setOrientation(LinearLayout.VERTICAL);
        outerUi.content.addView(personalPage);
        ui = new HomeUi(personalPage);
        HomeUi aboutYou = new HomeUi(ui.group());
        name = field(aboutYou, R.id.registrationName, R.string.profile_name, InputType.TYPE_CLASS_TEXT, 200);
        gender = field(aboutYou, R.id.registrationGender, R.string.profile_gender, InputType.TYPE_CLASS_TEXT, 200);
        selectionField(gender, () -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Odyssey_ProfilePicker)
                .setTitle("Gender").setItems(GENDERS, (dialog, which) -> gender.setText(GENDERS[which]))
                .setNegativeButton("Cancel", null).show());
        birthDate = field(aboutYou, R.id.registrationBirthDate, R.string.profile_birth_date, InputType.TYPE_CLASS_TEXT, 30);
        selectionField(birthDate, () -> BirthDatePicker.show(this, value(birthDate), date -> {
            legacyAge = null;
            birthDate.setText(date.isEmpty() ? PRIVATE : date);
        }));
        nationality = field(aboutYou, R.id.registrationNationality, R.string.profile_nationality, InputType.TYPE_CLASS_TEXT, 200);
        selectionField(nationality, () -> chooseCountry(true));
        // 第一页与偏好页各自保留控件，前后切换不会清空输入或已选标签。
        interestsPage = new LinearLayout(this); interestsPage.setOrientation(LinearLayout.VERTICAL);
        outerUi.content.addView(interestsPage);
        ui = new HomeUi(interestsPage);
        HomeUi place = new HomeUi(ui.group());
        location = field(place, R.id.registrationLocation, R.string.registration_location,
                InputType.TYPE_CLASS_TEXT, 200);
        // 框内文字打开国家→地区选择，右侧独立图标只负责主动定位。
        location.setFocusable(false); location.setShowSoftInputOnFocus(false);
        location.setOnClickListener(view -> { if (needsRegion) chooseRegion(); else chooseCountry(false); });
        locationAction = (android.widget.ImageView) ((LinearLayout) location.getParent()).getChildAt(2);
        locationAction.setImageResource(R.drawable.ic_my_location);
        locationAction.setImageTintList(android.content.res.ColorStateList.valueOf(ui.color(R.color.text_primary)));
        locationAction.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        locationAction.setContentDescription(getString(R.string.profile_use_location));
        locationAction.setBackgroundResource(ui.attr(android.R.attr.selectableItemBackgroundBorderless));
        locationAction.setOnClickListener(view -> { if (location.isEnabled()) currentLocation.start(); });
        controls.add(locationAction);
        currency = field(place, R.id.profileCurrency, R.string.profile_currency, InputType.TYPE_CLASS_TEXT, 100);
        selectionField(currency, this::chooseCurrency);
        locationStatus = ui.text("", 13, false);
        locationStatus.setVisibility(android.view.View.GONE);
        locationStatus.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        refreshCurrency();
        HomeUi interests = new HomeUi(ui.group());
        interests.content.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8));
        interests.text("Travel interests", 16, true);
        tags = new com.google.android.material.chip.ChipGroup(this);
        tags.setSingleSelection(false);
        tags.setChipSpacingHorizontal(ui.dp(6)); tags.setChipSpacingVertical(0);
        // 先放全部根标签，再放衍生标签：展开时始终出现在初始标签的下方。
        for (String root : InterestGroups.all().keySet()) addInterest(interests, root, tags);
        interests.content.addView(tags);
        // 单独一组保证相关选项从下一行开始，不挤进初始标签的最后一行。
        relatedTags = new com.google.android.material.chip.ChipGroup(this);
        relatedTags.setChipSpacingHorizontal(ui.dp(6)); relatedTags.setChipSpacingVertical(0);
        for (String[] children : InterestGroups.all().values())
            for (String child : children) addInterest(interests, child, relatedTags);
        interests.content.addView(relatedTags);
        updateInterestVisibility();
        ui = outerUi;
        // 隐私说明紧贴表单；弹性空白把主按钮固定在屏幕底部，内容超过一屏时高度为 0。
        TextView privacy = ui.text(getString(R.string.registration_privacy), 13, false);
        privacy.setTextColor(ui.color(R.color.text_primary));
        privacy.setGravity(android.view.Gravity.CENTER);
        ui.content.addView(new android.widget.Space(this), new LinearLayout.LayoutParams(-1, 0, 1f));
        status = ui.text("", 14, false);
        status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        previous = findViewById(R.id.registrationPrevious);
        previous.setOnClickListener(view -> getOnBackPressedDispatcher().onBackPressed());
        submit = ui.button(R.string.profile_next, () -> {
            if (readFailed) loadProfile();
            else if (profileStep == 0) showStep(1);
            else save();
        });
        submit.setId(R.id.registrationNext); submit.setTextSize(17);
        submit.setMinHeight(ui.dp(56)); submit.setInsetTop(0); submit.setInsetBottom(0);
        submit.setCornerRadius(getResources().getDimensionPixelSize(R.dimen.radius_medium));
        showStep(profileStep);
        if (editing) {
            MaterialButton cancel = ui.button(R.string.profile_cancel, this::finish);
            cancel.setId(R.id.registrationCancel); controls.add(cancel);
            cancel.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ui.color(R.color.surface)));
            cancel.setTextColor(ui.color(R.color.text_primary)); cancel.setTextSize(17);
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
        // 与行程规划共用分段进度：已到达的段为 lime，未到达的段为 outline。
        stepText.setText(profileStep == 0 ? "1/2" : "2/2");
        stepText.setContentDescription(profileStep == 0 ? "Step 1 of 2, about you" : "Step 2 of 2, your travel style");
        findViewById(R.id.registrationProgressStep2).setBackgroundColor(getColor(profileStep == 1 ? R.color.lime : R.color.outline));
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
    /** 选择框沿用同一行样式，但禁止唤出键盘。 */
    private void selectionField(TextInputEditText input, Runnable action) {
        input.setFocusable(false); input.setShowSoftInputOnFocus(false);
        input.setOnClickListener(view -> action.run());
    }
    private void addInterest(HomeUi ui, String id, com.google.android.material.chip.ChipGroup container) {
        com.google.android.material.chip.Chip chip = new com.google.android.material.chip.Chip(this);
        chip.setId(android.view.View.generateViewId()); chip.setText(PreferenceTags.ALL.get(id)); chip.setTag(id);
        chip.setCheckable(true); chip.setEnsureMinTouchTargetSize(true);
        chip.setCheckedIconVisible(false); chip.setChipIconVisible(false);
        chip.setTextSize(13); chip.setChipMinHeight(ui.dp(34));
        chip.setChipStartPadding(ui.dp(12)); chip.setChipEndPadding(ui.dp(12));
        chip.setTextStartPadding(ui.dp(2)); chip.setTextEndPadding(ui.dp(2));
        // 选中后纯色填充并加粗；触控区仍由 ensureMinTouchTargetSize 保证 48dp。
        chip.setChipBackgroundColorResource(R.color.chip_background);
        chip.setTextColor(androidx.core.content.ContextCompat.getColorStateList(this, R.color.chip_text));
        AppFonts.style(chip, android.graphics.Typeface.NORMAL);
        chip.setShapeAppearanceModel(chip.getShapeAppearanceModel().toBuilder().setAllCornerSizes(ui.dp(24)).build());
        chip.setChipStrokeWidth(ui.dp(1));
        chip.setChipStrokeColor(androidx.core.content.ContextCompat.getColorStateList(this, R.color.chip_stroke));
        chip.setOnCheckedChangeListener((button, checked) -> {
            AppFonts.style(chip, checked ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
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
    /** 自动币种仅是建议；手动指定后修改居住地不会覆盖用户选择。 */
    private void refreshCurrency() {
        if (currency == null) return;
        String suggested = CurrencyPreference.fromCountry(residenceCountry);
        currency.setText(currencyOverride.isEmpty() ? (suggested.isEmpty() ? "USD · Default" : suggested + " · Suggested") : currencyOverride + " · Selected");
    }
    private void chooseCurrency() {
        java.util.List<String> codes = new java.util.ArrayList<>();
        for (java.util.Currency item : java.util.Currency.getAvailableCurrencies())
            if (item.getDefaultFractionDigits() >= 0) codes.add(item.getCurrencyCode());
        java.util.Collections.sort(codes); codes.add(0, "Automatic from residence");
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_Odyssey_ProfilePicker)
                .setTitle("Display currency").setItems(codes.toArray(new String[0]), (dialog, which) -> {
                    currencyOverride = which == 0 ? "" : codes.get(which); refreshCurrency();
                }).setNegativeButton("Cancel", null).show();
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
                        currentLocation.cancel(); locationCountry = selected; residenceCountry = selected.code; location.setText(selected.name); refreshCurrency();
                        needsRegion = !regions.regions(selected.code).isEmpty();
                        if (needsRegion) chooseRegion();
                        else setLocationStatus("No regional subdivisions are listed for this country.");
                    }
                }).setNeutralButton(PRIVATE, (dialog, which) -> {
                    if (forNationality) nationality.setText(PRIVATE);
                    else { currentLocation.cancel(); locationCountry = null; needsRegion = false; residenceCountry = ""; location.setText(PRIVATE); refreshCurrency(); setLocationStatus(""); }
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
        residenceCountry = profile.optString("residence_country_code", "");
        if (residenceCountry.isEmpty()) residenceCountry = CurrencyPreference.countryFromLocation(value(location));
        currencyOverride = profile.optString("currency_override", ""); refreshCurrency();
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
    /** 左侧标题、右侧取值的合并行；整行都可点击，取值控件仍是原来的输入框。 */
    private TextInputEditText field(HomeUi ui, int id, int hint, int type, int limit) {
        LinearLayout row = ui.row(getString(hint));
        TextInputEditText input = new TextInputEditText(this);
        input.setId(id); input.setInputType(type);
        input.setContentDescription(getString(hint));
        input.setBackground(null);
        input.setPadding(ui.dp(12), ui.dp(8), 0, ui.dp(8));
        input.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
        input.setTextSize(15);
        AppFonts.medium(input);
        input.setTextColor(ui.color(R.color.text_primary));
        input.setHint(R.string.registration_optional);
        input.setHintTextColor(ui.color(R.color.text_secondary));
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});
        input.setSingleLine(true);
        input.setEllipsize(android.text.TextUtils.TruncateAt.END);
        // 品牌 lime 的光标在白色行上几乎看不见。
        if (android.os.Build.VERSION.SDK_INT >= 29) input.setTextCursorDrawable(R.drawable.profile_cursor);
        row.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));
        ui.rowIcon(row, R.drawable.ic_edit);
        row.setOnClickListener(view -> {
            if (!input.isEnabled()) return;
            if (!input.isFocusable()) { input.performClick(); return; }
            input.requestFocus();
            ((android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input, 0);
        });
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
                    .put("residence_country_code", InputValidation.countryCode(residenceCountry))
                    .put("currency_override", InputValidation.currencyCode(currencyOverride))
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
        for (android.view.View control : controls) control.setEnabled(!busy);
        previous.setEnabled(!busy);
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
                        .put("residence_country_code", residenceCountry).put("currency_override", currencyOverride)
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
