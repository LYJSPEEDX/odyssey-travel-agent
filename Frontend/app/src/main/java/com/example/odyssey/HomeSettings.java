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
        ui.text(activity.getString(R.string.settings_title), 28, true);
        section(R.string.settings_account_section);
        HomeUi account = new HomeUi(ui.group());
        account.actionRow(activity.getString(R.string.settings_login), activity.getString(R.string.settings_login_subtitle),
                () -> info(R.string.settings_login, R.string.settings_login_message)).setId(R.id.settingsLogin);
        account.actionRow(activity.getString(R.string.settings_edit_profile), preferences.nickname(activity),
                this::editProfile).setId(R.id.settingsProfile);
        account.actionRow(activity.getString(R.string.settings_reset_profile), "", this::confirmResetProfile);
        section(R.string.settings_preferences);
        String[] themes = activity.getResources().getStringArray(R.array.settings_theme_options);
        new HomeUi(ui.group()).actionRow(activity.getString(R.string.settings_theme), themes[preferences.themeIndex()],
                this::chooseTheme).setId(R.id.settingsTheme);
        section(R.string.settings_about_section);
        HomeUi about = new HomeUi(ui.group());
        about.actionRow(activity.getString(R.string.settings_about), "",
                () -> info(R.string.settings_about, R.string.settings_about_message));
        about.actionRow(activity.getString(R.string.settings_storage), "",
                () -> info(R.string.settings_storage, R.string.settings_storage_description));
        about.actionRow(activity.getString(R.string.settings_version), version(), null).setId(R.id.settingsVersion);
    }
    private void section(int title) {
        ui.text(activity.getString(title), 13, false).setPadding(ui.dp(4), ui.dp(8), 0, ui.dp(8));
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
    private void confirmResetProfile() {
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.settings_reset_profile)
                .setMessage(R.string.settings_reset_profile_message)
                .setNegativeButton(R.string.settings_cancel, null)
                .setPositiveButton(R.string.settings_reset_profile_confirm, (dialog, which) -> resetProfile()).show();
    }
    private void resetProfile() {
        android.content.Context app = activity.getApplicationContext();
        new Thread(() -> {
            boolean deleted = ProfileStore.delete(app);
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (!deleted) {
                    Toast.makeText(activity, R.string.settings_reset_profile_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                // 清空任务栈后重新打开首页：首页检查到没有资料，会自动进入注册页。
                activity.startActivity(new android.content.Intent(activity, HomeActivity.class)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK));
                activity.finish();
            });
        }, "profile-reset").start();
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
