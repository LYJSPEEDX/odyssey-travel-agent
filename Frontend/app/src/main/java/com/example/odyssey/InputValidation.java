package com.example.odyssey;

/** 统一处理文本边界；日期和业务状态仍由各自领域模型校验。 */
final class InputValidation {
    static String text(Object value) {
        if (value == null) return "";
        if (!(value instanceof String)) throw new IllegalArgumentException("Text must be a string");
        return ((String) value).replaceAll("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$", "");
    }
    static String optionalText(Object value, int maxLength, String field) {
        String normalized = text(value);
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }
    /** JSON 字段必须存在且为字符串，但允许空串；类型判断仍统一交给 text。 */
    static String typedText(Object value, int maxLength, String field) {
        if (value == null) throw new IllegalArgumentException(field + " must be a string");
        return optionalText(value, maxLength, field);
    }
    static String requiredText(Object value, int maxLength, String field) {
        String normalized = optionalText(value, maxLength, field);
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
    static int age(String value) {
        String normalized = text(value);
        try {
            if (!normalized.matches("[0-9]{1,3}")) throw new NumberFormatException();
            int years = Integer.parseInt(normalized);
            if (years >= 1 && years <= 120) return years;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("Age must be between 1 and 120");
    }
    private InputValidation() { }
}
