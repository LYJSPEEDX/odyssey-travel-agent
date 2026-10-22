package com.example.odyssey;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.InputFilter;
import android.text.InputType;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.content.pm.PackageInfoCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** 设置页的动态内容与弹窗；昵称和主题只保存在本机，登录入口目前仅显示说明。 */
final class HomeSettings {
    private final AppCompatActivity activity;
    private final HomeUi ui;
    private final AppPreferences preferences;
    private final Runnable refresh;

    HomeSettings(AppCompatActivity activity, HomeUi ui, Runnable refresh) {
        this.activity = activity;
        this.ui = ui;
        this.refresh = refresh;
        preferences = new AppPreferences(activity);
    }
    void render() {
        ui.title(R.string.settings_title, R.string.settings_subtitle);
        ui.section(R.string.settings_account_section);
        ui.card(activity.getString(R.string.settings_login), activity.getString(R.string.settings_login_subtitle),
                R.color.surface, () -> info(R.string.settings_login, R.string.settings_login_message))
                .setId(R.id.settingsLogin);
        ui.card(activity.getString(R.string.settings_edit_profile), preferences.nickname(activity),
                R.color.surface, this::editProfile).setId(R.id.settingsProfile);
        ui.section(R.string.settings_preferences);
        String[] themes = activity.getResources().getStringArray(R.array.settings_theme_options);
        ui.card(activity.getString(R.string.settings_theme), themes[preferences.themeIndex()],
                R.color.surface, this::chooseTheme).setId(R.id.settingsTheme);
        ui.section(R.string.settings_about_section);
        ui.card(activity.getString(R.string.settings_about), activity.getString(R.string.settings_about_subtitle),
                R.color.surface, () -> info(R.string.settings_about, R.string.settings_about_message));
        ui.card(activity.getString(R.string.settings_storage), activity.getString(R.string.local_only_notice),
                R.color.surface, () -> info(R.string.settings_storage, R.string.settings_storage_description));
        ui.card(activity.getString(R.string.settings_version), version(), R.color.surface, null)
                .setId(R.id.settingsVersion);
    }
    private void info(int title, int message) {
        new MaterialAlertDialogBuilder(activity).setTitle(title).setMessage(message)
                .setPositiveButton(R.string.dialog_ok, null).show();
    }
    private void editProfile() {
        LinearLayout form = new LinearLayout(activity);
        form.setPadding(ui.dp(24), ui.dp(8), ui.dp(24), ui.dp(8));
        form.setOrientation(LinearLayout.VERTICAL);
        AppCompatEditText name = new AppCompatEditText(activity);
        name.setId(R.id.profileNickname);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        name.setSingleLine(true);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        name.setHint(R.string.settings_profile_hint);
        name.setContentDescription(activity.getString(R.string.settings_nickname));
        name.setText(preferences.nickname(activity));
        form.addView(name, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.settings_edit_profile).setMessage(R.string.settings_profile_notice)
                .setView(form).setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(R.string.settings_save, null).create();
        // 在 show 后接管保存按钮：校验失败时保留弹窗和输入，避免默认行为自动关闭。
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            final String value;
            try { value = InputValidation.requiredText(name.getText() == null ? null : name.getText().toString(), 24, "Nickname"); }
            catch (IllegalArgumentException exception) { name.setError(activity.getString(R.string.settings_invalid_nickname)); return; }
            if (!preferences.saveNickname(value)) {
                name.setError(activity.getString(R.string.settings_save_failed)); return;
            }
            dialog.dismiss();
            refresh.run();
            Toast.makeText(activity, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        }));
        dialog.show();
    }
    private void chooseTheme() {
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.settings_theme)
                .setSingleChoiceItems(R.array.settings_theme_options, preferences.themeIndex(), (dialog, which) -> {
                    if (!preferences.saveTheme(which)) {
                        Toast.makeText(activity, R.string.settings_save_failed, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    dialog.dismiss();
                    preferences.applyTheme();
                    refresh.run();
                }).setNegativeButton(R.string.settings_cancel, null).show();
    }
    private String version() {
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return activity.getString(R.string.settings_version_format, info.versionName,
                    PackageInfoCompat.getLongVersionCode(info));
        } catch (PackageManager.NameNotFoundException exception) {
            return activity.getString(R.string.settings_version_unavailable);
        }
    }
}
