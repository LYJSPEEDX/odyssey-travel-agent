package com.example.odyssey;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

/** 本机昵称与主题偏好，与 TripStore 的行程数据分开保存；不代表账户登录状态。 */
final class AppPreferences {
    static final String FILE = "app_preferences";
    private final SharedPreferences preferences;

    AppPreferences(Context context) {
        preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
    String nickname(Context context) {
        return preferences.getString("nickname", context.getString(R.string.settings_guest));
    }
    boolean saveNickname(String value) {
        String name = value.trim();
        if (name.isEmpty() || name.length() > 24) return false;
        return preferences.edit().putString("nickname", name).commit();
    }
    int themeIndex() {
        // 0=跟随系统、1=浅色、2=深色；无效值回退到跟随系统，避免访问选项数组越界。
        int index = preferences.getInt("theme", 0);
        return index >= 0 && index <= 2 ? index : 0;
    }
    boolean saveTheme(int index) {
        return index >= 0 && index <= 2 && preferences.edit().putInt("theme", index).commit();
    }
    void applyTheme() {
        // 修改全局日夜主题可能触发 Activity 重建，因此页面临时状态需通过 Bundle 恢复。
        int index = themeIndex();
        AppCompatDelegate.setDefaultNightMode(index == 1 ? AppCompatDelegate.MODE_NIGHT_NO
                : index == 2 ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
}
