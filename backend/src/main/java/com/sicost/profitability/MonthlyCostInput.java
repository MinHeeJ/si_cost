package com.sicost.profitability;

import com.sicost.common.validation.ProjectInputPolicy;
import java.util.Set;
import org.springframework.util.MultiValueMap;

public final class MonthlyCostInput {
    private MonthlyCostInput() {}
    public record Query(int year, int month, String personId) {}
    public static Query query(MultiValueMap<String, String> values) {
        values.forEach((key, entries) -> {
            if (!Set.of("year", "month", "personId").contains(key) || entries.size() != 1)
                throw ProjectInputPolicy.invalid(key);
        });
        int year = integer(values.getFirst("year"), "year");
        int month = integer(values.getFirst("month"), "month");
        if (year < 1 || year > 9999) throw ProjectInputPolicy.invalid("year");
        if (month < 1 || month > 12) throw ProjectInputPolicy.invalid("month");
        String person = values.containsKey("personId") ? ProjectInputPolicy.text(values.getFirst("personId"), 20, "personId") : null;
        return new Query(year, month, person);
    }
    private static int integer(String text, String field) {
        if (text == null || !text.matches("[0-9]{1,4}")) throw ProjectInputPolicy.invalid(field);
        return Integer.parseInt(text);
    }
}
