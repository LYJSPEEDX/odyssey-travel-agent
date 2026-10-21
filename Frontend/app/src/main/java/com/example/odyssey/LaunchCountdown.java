package com.example.odyssey;

/** 只计算欢迎页在前台展示的时间；不依赖 Android，便于单元测试。 */
final class LaunchCountdown {
    private long remainingMillis; // 暂停时尚未展示的毫秒数。
    private long resumedAt; // 最近一次进入前台的单调时钟时间，不使用可能被用户修改的日期时间。
    private boolean running;

    LaunchCountdown(long remainingMillis) {
        this.remainingMillis = Math.max(0, remainingMillis);
    }

    void resume(long now) {
        if (running) return; // 重复收到回调时不重新计时。
        resumedAt = now;
        running = true;
    }

    void pause(long now) {
        remainingMillis = remaining(now); // 先扣除已展示时间，再切换成暂停状态。
        running = false;
    }

    long remaining(long now) {
        long elapsed = running ? Math.max(0, now - resumedAt) : 0;
        return Math.max(0, remainingMillis - elapsed); // 到期后始终是零，不返回负数。
    }
}
