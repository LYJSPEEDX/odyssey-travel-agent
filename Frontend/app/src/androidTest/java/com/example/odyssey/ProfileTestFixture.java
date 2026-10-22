package com.example.odyssey;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.rules.ExternalResource;
import java.io.File;
import java.nio.file.Files;

/** 已完成资料的测试前提；测试结束按字节恢复原文件，不删除模拟器中的用户资料。 */
final class ProfileTestFixture extends ExternalResource {
    private final byte[][] previous = new byte[3][];
    private File[] files;

    @Override protected void before() throws Throwable {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        files = new File[]{new File(context.getFilesDir(), "profile.json"),
                new File(context.getFilesDir(), "profile.json.bak"),
                new File(context.getFilesDir(), "profile.json.new")};
        for (int i = 0; i < files.length; i++) {
            previous[i] = files[i].exists() ? Files.readAllBytes(files[i].toPath()) : null;
        }
        // 完整备份后才隔离 AtomicFile 的恢复文件，避免测试读取用户旧备份。
        try {
            for (File file : files) Files.deleteIfExists(file.toPath());
            new ProfileStore(context).save(new JSONObject().put("schema_version", 2)
                    .put("name", "Test traveller").put("gender", "Prefer not to say")
                    .put("age", 30).put("nationality", "Australian")
                    .put("residential_location", "Sydney").put("travel_preferences", "")
                    .put("preference_tags", new JSONArray().put("DIVING")));
        } catch (Throwable failure) {
            after();
            throw failure;
        }
    }

    @Override protected void after() {
        if (files == null) return;
        try {
            for (int i = 0; i < files.length; i++) {
                if (previous[i] == null) Files.deleteIfExists(files[i].toPath());
                else Files.write(files[i].toPath(), previous[i]);
            }
        } catch (java.io.IOException error) {
            throw new AssertionError("Could not restore original profile fixture", error);
        }
    }
}
