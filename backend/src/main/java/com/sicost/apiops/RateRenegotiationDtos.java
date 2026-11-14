package com.sicost.apiops;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

public final class RateRenegotiationDtos {
    private RateRenegotiationDtos() {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Issue(String code, String message, String field, String developerId, String assignmentId,
                        String projectId, String month, String source, boolean blocking) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Row(String assignmentId, String developerId, String name, String managementNo, String employeeType,
        String projectId, String projectName, String renewalDate, int daysUntilRenewal, boolean planned,
        String contractMm, String monthlyMm, String unitPrice, String monthlyRevenue, String monthlyLaborCost,
        String marginRate, String targetUnitPrice, String requiredIncreaseRate, String annualGain,
        String targetAnnualGain, List<Issue> issues) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Summary(int personCount, int assignmentCount, String weightedMarginRate, String annualGain, String targetAnnualGain) {}
    public record Page(int page, int size, int total) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Result(String baseMonth, OffsetDateTime calculatedAt, String appliedIncreaseRate,
        String targetMarginRate, Summary summary, List<Row> items, Page pagination, int unevaluatedCount,
        List<Issue> unevaluatedAssignments, List<Issue> issues) {}
}
