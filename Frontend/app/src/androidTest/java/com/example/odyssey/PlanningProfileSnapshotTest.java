package com.example.odyssey;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;

/** 打开问卷时从已保存资料取得 UID 和快照，后续仅交给本地 Mock。 */
public class PlanningProfileSnapshotTest {
    @org.junit.Rule public final ProfileTestFixture fixture = new ProfileTestFixture();
    @Test public void planningStartsWithSavedProfileUid() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String uid = new ProfileStore(context).load().getString("uid");
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    java.lang.reflect.Field field = MainActivity.class.getDeclaredField("session"); field.setAccessible(true);
                    PlanningSession session = (PlanningSession) field.get(activity);
                    JSONObject wrapper = new JSONObject(session.profileRequest);
                    assertEquals(uid, wrapper.getJSONObject("profile").getString("uid"));
                    assertEquals("Test traveller", wrapper.getJSONObject("profile").getString("displayName"));
                    assertEquals("DIVING", wrapper.getJSONObject("profile").getJSONArray("travelTagIds").getString(0));
                } catch (Exception error) { throw new AssertionError(error); }
            });
        }
    }
}
