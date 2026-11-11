package com.sicost.uploadsops;

import com.sicost.common.api.ApiException;
import com.sicost.common.validation.InputValidator;
import java.util.Set;
import org.springframework.util.MultiValueMap;

public final class UploadIssuesInput {
    private UploadIssuesInput() {}
    public static long id(String value) {
        if (value == null || !value.matches("[0-9]{1,19}")) throw InputValidator.invalid("id");
        try {
            long id = Long.parseLong(value);
            if (id < 1) throw InputValidator.invalid("id");
            return id;
        } catch (NumberFormatException e) { throw InputValidator.invalid("id"); }
    }
    public static void allow(MultiValueMap<String, String> query, boolean paged) {
        Set<String> allowed = paged ? Set.of("severity", "page", "size") : Set.of("severity");
        query.forEach((key, values) -> {
            if (!allowed.contains(key)) throw new ApiException(400, "UNKNOWN_FIELD", "허용되지 않은 입력 항목입니다.", key, null);
            if (values.size() != 1) throw InputValidator.invalid(key);
        });
    }
    public static String severity(String value) {
        if (value != null && !Set.of("ERR", "WARN").contains(value)) throw InputValidator.invalid("severity");
        return value;
    }
    public static int integer(String value, int fallback, String field) {
        if (value == null) return fallback;
        if (!value.matches("[0-9]{1,10}")) throw InputValidator.invalid(field);
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { throw InputValidator.invalid(field); }
    }
}
