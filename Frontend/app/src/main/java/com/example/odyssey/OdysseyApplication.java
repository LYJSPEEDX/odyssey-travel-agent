package com.example.odyssey;

import android.app.Application;

/** 应用进程启动入口：在 Activity 创建前应用已保存的主题，保证各页面主题一致。 */
public class OdysseyApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        new AppPreferences(this).applyTheme();
    }
}
