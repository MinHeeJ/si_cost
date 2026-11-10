package com.sicost.common.validation;

import com.sicost.common.api.ApiException;
import java.util.Locale;
import java.util.Objects;

public final class InputValidator {
    private InputValidator() {}
    public static String text(String value, int max, String field) {
        if (value == null) throw invalid(field);
        String trimmed = value.strip();
        if (trimmed.isEmpty() || trimmed.codePointCount(0, trimmed.length()) > max) throw invalid(field);
        return trimmed;
    }
    public static String optionalText(String value, int max, String field) {
        if (value == null || value.isBlank()) return null;
        return text(value, max, field);
    }
    public static String groupId(String value) {
        String result = text(value, 30, "groupId").toUpperCase(Locale.ROOT);
        if (!result.matches("[A-Z][A-Z0-9_]{0,29}")) throw invalid("groupId");
        return result;
    }
    public static String code(String value) {
        String result = text(value, 20, "code").toUpperCase(Locale.ROOT);
        if (!result.matches("[A-Z0-9_]{1,20}")) throw invalid("code");
        return result;
    }
    public static String yesNo(String value, String field) {
        String result = text(value, 1, field);
        if (!result.equals("Y") && !result.equals("N")) throw invalid(field);
        return result;
    }
    public static void immutable(Object original, Object submitted, String field) {
        if (!Objects.equals(original, submitted)) throw invalid(field);
    }
    public static int page(int page) {
        if (page < 1) throw invalid("page");
        return page;
    }
    public static int size(Integer size) {
        if (size == null) return 50;
        if (size != 20 && size != 50 && size != 100) throw invalid("size");
        return size;
    }
    public static ApiException invalid(String field) {
        return new ApiException(400, "VALIDATION_ERROR", "입력 항목의 형식과 길이를 확인해 주세요.", field, null);
    }
}
