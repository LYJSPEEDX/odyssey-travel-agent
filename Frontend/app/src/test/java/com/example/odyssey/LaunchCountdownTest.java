package com.example.odyssey;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** 纯 Java 测试：不用真等 1.5 秒，通过传入虚拟时间验证生命周期。 */
public class LaunchCountdownTest {
    @Test public void lastsExactlyFifteenHundredMilliseconds() {
        LaunchCountdown countdown = new LaunchCountdown(1500);
        countdown.resume(100);
        assertEquals(1, countdown.remaining(1599));
        assertEquals(0, countdown.remaining(1600));
        assertEquals(0, countdown.remaining(2000));
    }

    @Test public void backgroundTimeDoesNotConsumeVisibleTime() {
        LaunchCountdown countdown = new LaunchCountdown(1500);
        countdown.resume(100);
        countdown.pause(600);
        assertEquals(1000, countdown.remaining(5000));
        countdown.resume(5000);
        assertEquals(0, countdown.remaining(6000));
    }

    @Test public void recreationRestoresOnlyRemainingTime() {
        LaunchCountdown old = new LaunchCountdown(1500);
        old.resume(0);
        LaunchCountdown restored = new LaunchCountdown(old.remaining(800));
        restored.resume(1000);
        assertEquals(700, restored.remaining(1000));
        assertEquals(0, restored.remaining(1700));
    }

    @Test public void repeatedLifecycleCallsDoNotRestartTimer() {
        LaunchCountdown countdown = new LaunchCountdown(1500);
        countdown.resume(0);
        countdown.resume(500);
        countdown.pause(1000);
        countdown.pause(2000);
        assertEquals(500, countdown.remaining(3000));
    }
}
