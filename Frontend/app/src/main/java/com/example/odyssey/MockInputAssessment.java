package com.example.odyssey;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** 本地演示规则，不是 AI 置信度，也不验证真实地点或语义。 */
final class MockInputAssessment {
    private static final Set<String> DEMO_PLACES = new HashSet<>(Arrays.asList(
            "sydney", "melbourne", "brisbane", "perth", "adelaide", "canberra", "tokyo", "kyoto", "bali", "london", "new york", "seoul"));
    private MockInputAssessment() { }

    static boolean acceptable(int step, String input) {
        String text = input.trim().toLowerCase(Locale.ROOT);
        if (step < 2) return DEMO_PLACES.contains(text);
        if (step == 2) return text.contains("flexible") || text.contains("not fixed")
                || text.contains("not sure") || text.contains("decide for me");
        // 仅拦截明显无法理解的演示输入，不能宣称已对所有自由文本做语义验证。
        if (!text.matches("(?s).*[\\p{L}].*") || text.matches("[\\p{L}]{1,2}")) return false;
        if (step == 4 && text.matches("[\\d\\s.,]+")) return false;
        return true;
    }

    static String clarification(int step) {
        if (step < 2) return "Demo: I cannot verify this place yet. Try an example city such as Perth or Sydney.";
        if (step == 2) return "Demo: Please select dates on the calendar or tell me your dates are flexible.";
        if (step == 4) return "Demo: Please describe your budget and include the currency and whether it is per person or total.";
        return "Demo: I am not sure what you mean. Could you add a little more detail?";
    }
}
