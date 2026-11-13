package com.sicost.costanalysis;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;

public final class ProjectCostDtos {
    private ProjectCostDtos() {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MissingReason(String code,String message,String month,String personId,String assignmentId,String metric) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Completeness(boolean costComplete,boolean revenueComplete,boolean estimated,List<MissingReason> issues,
        int unknownProjectCount,String rangeStartMonth,String rangeEndMonth,String missingBeforeMonth) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Alert(String kind,String message,String actualNumerator,String actualDenominator,String threshold,String comparison,String primaryCause) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Row(String projectId,String projectName,String contractType,String status,String originalContractAmount,String contractAmount,
        String planMm,String actualMm,String cumulativeRevenue,String cumulativeCost,String cumulativeProfit,String cumulativeMargin,
        String forecastMargin,String monthlyMargin,String targetMargin,String targetGap,String budgetAmount,String budgetUsage,
        String pricePerMm,String costPerMm,String profitPerMm,String freeMm,String freeLaborCost,int alertCount,List<Alert> alerts,
        Boolean budgetWarning,String primaryCause,Completeness completeness) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ForecastPerson(String personId,String personName,String remainingMm,String unitCostSource,String sourceMonth,
        String unitCostNumerator,String unitCostDenominator,String contribution,List<MissingReason> issues) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Forecast(String baseMonth,String remainingMm,int personCount,int missingUnitCostPersonCount,String knownRemainingCost,
        boolean complete,List<ForecastPerson> people,List<MissingReason> issues) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SimulationDefaults(boolean enabled,String reason,int n,String c,int m,String r) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Detail(String projectId,String month,LocalDate baseDate,String sourceStatus,Row row,Forecast forecast,
        SimulationDefaults simulationDefaults,String revenueExplanation,Completeness completeness) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MonthlyRow(String month,String actualMm,String laborAllocated,String teamExpense,String projectExpense,String directExpense,
        String additionalDirectExpense,String totalCost,String revenue,String turnkeyReferenceRevenue,String referenceMargin,boolean fixed,Completeness completeness) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Months(String projectId,String month,List<MonthlyRow> items,String totalCost,Completeness completeness) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AssignmentRow(String month,String assignmentId,String personId,String personName,String managementNumber,String jobCode,
        String jobName,LocalDate startDate,LocalDate endDate,String assignmentMm,String freeMm,String laborAllocated,String teamExpense,
        String projectExpense,String directExpense,String personalPrice,String inputType,String assignmentStatus,boolean planned,boolean fixed,Completeness completeness) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Assignments(String projectId,String month,List<AssignmentRow> items,Completeness completeness) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Cause(String kind,String amount,String formula,List<MissingReason> issues,String targetScreenId,String projectId) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Causes(String projectId,String month,List<Cause> items,String actualProgress,String progressExcess,String remainder,
        boolean showUnexplained,boolean highlight,String message,List<MissingReason> issues) {}
    public record SimulationInput(String month,int n,String c,Integer m,String r) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record SimulationResult(String projectId,String month,String beforeMargin,String afterMargin,String differencePp,String beforeTargetGap,
        String afterTargetGap,String virtualRevenue,String virtualCost,boolean saved,String message,Completeness completeness) {}
}
