package com.sicost.workforceoperationsops;

import com.sicost.apiops.StaffingQueryInput;
import com.sicost.common.TimeConfiguration;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public final class WorkforceOperationsExportInput {
    private WorkforceOperationsExportInput() {}
    public record Query(int year, String tab, String amountUnit, MultiValueMap<String,String> calendarFilters) {}

    public static Query parse(MultiValueMap<String,String> values, String body, Clock clock) {
        StaffingQueryInput.validate(values, Set.of("year", "jobCode", "gradeCode", "employeeType", "remainingOnly", "amountUnit", "tab"), body);
        String tab = StaffingQueryInput.choice(values, "tab", Set.of("CALENDAR", "SUPPLY_DEMAND"), null);
        if (tab == null) throw StaffingQueryInput.invalid("tab", "VALIDATION_ERROR");
        int current = LocalDate.now(clock.withZone(TimeConfiguration.SEOUL)).getYear();
        int year = StaffingQueryInput.integer(values.getFirst("year"), current, "year");
        if (year > Math.min(9999, current + 1)) throw StaffingQueryInput.invalid("year", "INVALID_PERIOD");
        StaffingQueryInput.text(values, "jobCode", 10);
        StaffingQueryInput.text(values, "gradeCode", 10);
        StaffingQueryInput.choice(values, "employeeType", Set.of("FT", "CT", "FL", "OS"), null);
        StaffingQueryInput.choice(values, "remainingOnly", Set.of("true", "false"), "false");
        String unit = StaffingQueryInput.choice(values, "amountUnit", Set.of("WON", "THOUSAND", "MILLION"), "THOUSAND");
        var filters = new LinkedMultiValueMap<String,String>();
        filters.set("year", Integer.toString(year));
        for (String field : Set.of("jobCode", "gradeCode", "employeeType", "remainingOnly")) {
            if (values.containsKey(field)) filters.set(field, values.getFirst(field));
        }
        return new Query(year, tab, unit, filters);
    }
}
