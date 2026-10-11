package com.example.odyssey;

import android.app.Application;

public class OdysseyApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        new AppPreferences(this).applyTheme();
    }
}
