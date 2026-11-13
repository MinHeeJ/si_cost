package com.sicost.profitability;

import com.sicost.common.validation.ProjectInputPolicy;
import java.util.Set;
import org.springframework.util.MultiValueMap;

public final class IndividualProfitabilityInput {
    private IndividualProfitabilityInput() {}
    public static final Set<String> SORTS = Set.of("name", "companyName", "employeeType", "hireDate",
        "utilization", "mm", "revenue", "totalCost", "profit", "profitRate");
    public record Query(Integer year, String view, boolean includeTurnkey, String name, String employeeType,
        String companyId, String partId, String jobCode, String show, int page, int size, String sort, String direction) {
        public Query withPage(int next) { return new Query(year, view, includeTurnkey, name, employeeType,
            companyId, partId, jobCode, show, next, size, sort, direction); }
        public Query withTurnkey(boolean include) { return new Query(year, view, include, name, employeeType,
            companyId, partId, jobCode, show, page, size, sort, direction); }
    }
    public static Query query(MultiValueMap<String, String> values, boolean detail) {
        Set<String> allowed = Set.of("year", "view", "includeTurnkey", "name", "employeeType", "companyId",
            "partId", "jobCode", "show", "page", "size", "sort", "direction");
        values.forEach((key, entries) -> {
            if (!allowed.contains(key) || entries.size() != 1 || (detail && Set.of("page", "size").contains(key)))
                throw ProjectInputPolicy.invalid(key);
        });
        Integer year = values.containsKey("year") ? integer(values.getFirst("year"), "year") : null;
        String view = choice(values, "view", "CUMULATIVE", Set.of("CUMULATIVE", "MONTHLY_AVERAGE"));
        String turnkey = choice(values, "includeTurnkey", "true", Set.of("true", "false"));
        String name = values.getFirst("name");
        if (name != null) { name = name.strip(); if (name.length() > 100) throw ProjectInputPolicy.invalid("name"); }
        int page = values.containsKey("page") ? integer(values.getFirst("page"), "page") : 1;
        int size = values.containsKey("size") ? integer(values.getFirst("size"), "size") : 50;
        if (page < 1) throw ProjectInputPolicy.invalid("page");
        if (!Set.of(20, 50, 100).contains(size)) throw ProjectInputPolicy.invalid("size");
        String sort = values.getFirst("sort");
        if (sort != null && !SORTS.contains(sort)) throw ProjectInputPolicy.invalid("sort");
        return new Query(year, view, "true".equals(turnkey), name, text(values, "employeeType"),
            text(values, "companyId"), text(values, "partId"), text(values, "jobCode"),
            choice(values, "show", "ALL", Set.of("ALL", "LOSS", "CONCURRENT")), page, size, sort,
            choice(values, "direction", "ASC", Set.of("ASC", "DESC")));
    }
    private static String text(MultiValueMap<String, String> values, String key) {
        return values.containsKey(key) ? ProjectInputPolicy.text(values.getFirst(key), 20, key) : null;
    }
    private static String choice(MultiValueMap<String, String> values, String key, String fallback, Set<String> allowed) {
        String value = values.containsKey(key) ? values.getFirst(key) : fallback;
        if (value == null || !allowed.contains(value)) throw ProjectInputPolicy.invalid(key);
        return value;
    }
    private static int integer(String value, String field) {
        try { if (value == null || !value.matches("[0-9]{1,10}")) throw ProjectInputPolicy.invalid(field);
            return Integer.parseInt(value); }
        catch (NumberFormatException e) { throw ProjectInputPolicy.invalid(field); }
    }
}
