package com.sicost.salespartsettlements;

import com.sicost.common.validation.ProjectInputPolicy;
import java.util.Set;
import org.springframework.util.MultiValueMap;

public final class SalesPartSettlementInput {
    private SalesPartSettlementInput() {}
    public static final Set<String> SORTS = Set.of("name", "employeeType", "mm", "unitPrice", "payAmount", "costAmount", "profitAmount", "profitShareAmount");
    public record Query(String month, int page, int size, String sort, String direction) {}
    public static Query query(MultiValueMap<String,String> values, boolean detail) {
        Set<String> allowed = detail ? Set.of("month", "page", "size", "sort", "direction") : Set.of("month");
        values.forEach((key, entries) -> {
            if (!allowed.contains(key) || entries.size() != 1) throw ProjectInputPolicy.invalid(key);
        });
        String month = values.getFirst("month");
        if (values.containsKey("month")) {
            if (month == null || !month.matches("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])")) throw ProjectInputPolicy.invalid("month");
            ProjectInputPolicy.month(month, "month");
        }
        int page = integer(values, "page", 1), size = integer(values, "size", 50);
        if (page < 1) throw ProjectInputPolicy.invalid("page");
        if (!Set.of(20,50,100).contains(size)) throw ProjectInputPolicy.invalid("size");
        String sort = values.containsKey("sort") ? values.getFirst("sort") : "name";
        String direction = values.containsKey("direction") ? values.getFirst("direction") : "ASC";
        if (!SORTS.contains(sort)) throw ProjectInputPolicy.invalid("sort");
        if (!Set.of("ASC","DESC").contains(direction)) throw ProjectInputPolicy.invalid("direction");
        return new Query(month,page,size,sort,direction);
    }
    private static int integer(MultiValueMap<String,String> values,String key,int fallback) {
        if (!values.containsKey(key)) return fallback;
        String value = values.getFirst(key);
        try {
            if (value == null || !value.matches("[0-9]{1,10}")) throw ProjectInputPolicy.invalid(key);
            return Integer.parseInt(value);
        } catch (NumberFormatException e) { throw ProjectInputPolicy.invalid(key); }
    }
}
