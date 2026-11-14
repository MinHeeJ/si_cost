package com.sicost.apiops;

import com.sicost.common.validation.ProjectInputPolicy;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.util.MultiValueMap;

public final class RateRenegotiationInput {
    private RateRenegotiationInput() {}
    public static final Set<String> SORTS = Set.of("name", "projectName", "renewalDate", "contractMm", "unitPrice",
        "monthlyLaborCost", "marginRate", "targetUnitPrice", "requiredIncreaseRate", "annualGain");
    public record Query(String renewalWindow, String marginBelow, String employeeType, String projectId,
                        BigDecimal increaseRate, int page, int size, String sort, String direction) {}
    public static Query query(MultiValueMap<String, String> values) {
        values.forEach((key, entries) -> {
            if (!Set.of("renewalWindow", "marginBelow", "employeeType", "projectId", "increaseRate", "page", "size", "sort", "direction").contains(key)
                || entries.size() != 1) throw ProjectInputPolicy.invalid(key);
        });
        String employee = values.getFirst("employeeType"), project = values.getFirst("projectId");
        if (employee != null && !Set.of("FT", "CT", "FL", "OS").contains(employee)) throw ProjectInputPolicy.invalid("employeeType");
        if (project != null && (project.isBlank() || project.length() > 20 || !project.equals(project.trim()))) throw ProjectInputPolicy.invalid("projectId");
        int page = integer(values.getFirst("page"), 1, "page"), size = integer(values.getFirst("size"), 50, "size");
        if (!Set.of(20, 50, 100).contains(size)) throw ProjectInputPolicy.invalid("size");
        return new Query(option(values, "renewalWindow", "183", Set.of("92", "183", "366", "ALL")),
            option(values, "marginBelow", "15", Set.of("10", "15", "20", "ALL")), employee, project,
            rate(values.getFirst("increaseRate")), page, size, option(values, "sort", "marginRate", SORTS),
            option(values, "direction", "ASC", Set.of("ASC", "DESC")));
    }
    public static BigDecimal rate(String value) {
        if (value == null) return new BigDecimal("5");
        String text = value.trim();
        if (text.isEmpty()) return BigDecimal.ZERO;
        if (text.length() > 200 || !text.matches("-?[0-9]+(?:\\.[0-9]+)?")) throw ProjectInputPolicy.invalid("increaseRate");
        BigDecimal rate = new BigDecimal(text);
        if (rate.signum() < 0) return BigDecimal.ZERO;
        if (rate.compareTo(new BigDecimal("50")) > 0) return new BigDecimal("50");
        if (rate.scale() > 2) throw ProjectInputPolicy.invalid("increaseRate");
        return rate.stripTrailingZeros();
    }
    private static String option(MultiValueMap<String, String> values, String key, String fallback, Set<String> allowed) {
        String value = values.containsKey(key) ? values.getFirst(key) : fallback;
        if (!allowed.contains(value)) throw ProjectInputPolicy.invalid(key);
        return value;
    }
    private static int integer(String value, int fallback, String field) {
        if (value == null) return fallback;
        try {
            if (!value.matches("[0-9]{1,10}")) throw ProjectInputPolicy.invalid(field);
            int parsed = Integer.parseInt(value);
            if (parsed < 1) throw ProjectInputPolicy.invalid(field);
            return parsed;
        } catch (NumberFormatException e) { throw ProjectInputPolicy.invalid(field); }
    }
}
