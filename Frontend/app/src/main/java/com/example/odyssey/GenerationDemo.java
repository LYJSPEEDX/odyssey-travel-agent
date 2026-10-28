package com.example.odyssey;

/** 本地演示事件时间表；将来由 SSE 事件驱动同样的阶段状态，而不是按时间猜测。 */
final class GenerationDemo {
    static final long DURATION_MS = 140_000L;
    static final String[] TITLES = {"Getting ready", "Exploring transport", "Finding places to stay",
            "Exploring your destination", "Shaping your itinerary"};
    static final String[] DETAILS = {"Your travel preferences are ready for this demo.",
            "Considering possible ways to get there.", "Exploring potential accommodation.",
            "Finding ideas for places and food.", "Putting the days together."};
    private static final long[] STARTS = {0L, 12_000L, 40_000L, 72_000L, 108_000L};
    static int stage(long elapsed) {
        int result = 0;
        for (int i = 1; i < STARTS.length; i++) if (elapsed >= STARTS[i]) result = i;
        return result;
    }
    private GenerationDemo() { }
}
