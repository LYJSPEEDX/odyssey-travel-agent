package com.example.odyssey;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

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
        int index = preferences.getInt("theme", 0);
        return index >= 0 && index <= 2 ? index : 0;
    }
    boolean saveTheme(int index) {
        return index >= 0 && index <= 2 && preferences.edit().putInt("theme", index).commit();
    }
    void applyTheme() {
        int index = themeIndex();
        AppCompatDelegate.setDefaultNightMode(index == 1 ? AppCompatDelegate.MODE_NIGHT_NO
                : index == 2 ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }
}
