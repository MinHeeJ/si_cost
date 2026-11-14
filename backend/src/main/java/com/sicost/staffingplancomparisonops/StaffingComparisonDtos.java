package com.sicost.staffingplancomparisonops;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class StaffingComparisonDtos {
    private StaffingComparisonDtos() {}
    public record Query(Integer year, String projectId, String difference, String amountUnit) {}
    public record Project(String id, String name, LocalDate startDate, LocalDate endDate, String status, String contractType) {}
    public record Plan(String projectId, String jobCode, BigDecimal planMm) {}
    public record Row(String projectId, String projectName, String jobCode, BigDecimal planMm, BigDecimal actualMm, BigDecimal differenceMm) {}
    public record Result(int year, List<Row> rows, BigDecimal planMm, BigDecimal actualMm, BigDecimal differenceMm) {}
}
