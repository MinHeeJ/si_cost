package com.sicost.common.workforce;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public final class WorkforceProjectionDtos {
    private WorkforceProjectionDtos() {}
    public record Query(YearMonth from, int months, boolean includeUnfilled, YearMonth standardMonth) {}
    public record Person(String id, String jobCode, LocalDate joinDate, LocalDate retireDate) {}
    public record Leave(String developerId, LocalDate startDate, LocalDate endDate) {}
    public record Assignment(String id, String projectStatus, String contractType) {}
    public record Demand(String jobCode, String startMonth, int months, int count, BigDecimal monthlyMm, BigDecimal probability) {}
    public record Standard(String code, BigDecimal value) {}
    public record Month(String jobCode, YearMonth month, BigDecimal supply, BigDecimal demand, BigDecimal shortage) {}
    public record Issue(String field, String reason) {}
    public record Result(List<Month> months, List<Issue> issues) {}
}
