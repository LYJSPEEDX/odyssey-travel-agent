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
    /** 生日允许不透露；输入日期必须真实存在且不能晚于今天。 */
    static String birthDate(Object value) {
        String date = optionalText(value, 10, "Date of birth");
        if (date.isEmpty()) return "";
        if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
            throw new IllegalArgumentException("Choose a complete date of birth");
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
        format.setLenient(false);
        try {
            java.util.Date parsed = format.parse(date);
            if (parsed == null || parsed.after(new java.util.Date()))
                throw new IllegalArgumentException("Date of birth cannot be in the future");
        } catch (java.text.ParseException error) { throw new IllegalArgumentException("Choose a valid date of birth"); }
        return date;
    }
    static String countryCode(Object value) {
        String code = optionalText(value, 2, "Country code").toUpperCase(java.util.Locale.ROOT);
        if (!code.isEmpty() && !java.util.Arrays.asList(java.util.Locale.getISOCountries()).contains(code))
            throw new IllegalArgumentException("Invalid country code");
        return code;
    }
    static String currencyCode(Object value) {
        String code = optionalText(value, 3, "Currency").toUpperCase(java.util.Locale.ROOT);
        if (!code.isEmpty()) {
            try { if (java.util.Currency.getInstance(code).getDefaultFractionDigits() < 0) throw new IllegalArgumentException(); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid currency"); }
        }
        return code;
    }
    /** UID 使用标准 UUID 字符串，不从姓名、设备号或个人信息生成。 */
    static String uid(Object value) {
        String id = requiredText(value, 36, "UID");
        try {
            java.util.UUID parsed = java.util.UUID.fromString(id);
            if (!parsed.toString().equals(id)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException error) { throw new IllegalArgumentException("Invalid UID"); }
        return id;
    }
    private InputValidation() { }
}
