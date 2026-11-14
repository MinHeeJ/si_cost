package com.sicost.candidatesops;

import com.sicost.common.api.ApiException;
import com.sicost.common.validation.InputValidator;
import java.util.Set;
import org.springframework.util.MultiValueMap;

public final class CandidateMatchingInput {
    private CandidateMatchingInput() {}
    public record Query(int page, int size, String sort, String direction) {}
    public static void query(MultiValueMap<String, String> values, Set<String> allowed) {
        values.forEach((field, entries) -> {
            if (!allowed.contains(field)) throw new ApiException(400, "UNKNOWN_FIELD", "허용되지 않은 입력 항목입니다.", field, null);
            if (entries.size() != 1) throw InputValidator.invalid(field);
        });
    }
    public static Query shortages(MultiValueMap<String, String> values) {
        query(values, Set.of("page", "size", "sort", "direction"));
        int page = integer(values.getFirst("page"), 1, "page");
        int size = integer(values.getFirst("size"), 50, "size");
        InputValidator.page(page); InputValidator.size(size);
        String sort = values.getFirst("sort"), direction = values.getFirst("direction");
        if (sort != null && !Set.of("firstShortageMonth", "maximumShortage", "jobCode").contains(sort)) throw InputValidator.invalid("sort");
        if (direction != null && !Set.of("ASC", "DESC").contains(direction)) throw InputValidator.invalid("direction");
        return new Query(page, size, sort, direction == null ? "ASC" : direction);
    }
    private static int integer(String value, int fallback, String field) {
        if (value == null) return fallback;
        if (!value.matches("[0-9]+")) throw InputValidator.invalid(field);
        try { return Integer.parseInt(value); } catch (NumberFormatException e) { throw InputValidator.invalid(field); }
    }
    public static String id(String value) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > 20) throw InputValidator.invalid("id");
        return value;
    }
}
