package com.sicost.apiops;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sicost.notificationsops.NotificationDtos.Link;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class DashboardDtos {
    private DashboardDtos() {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Basis(String basisMonth,LocalDate basisDate,String sourceService,OffsetDateTime observedAt,String status,List<String> reasons) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Metric(String metricCode,String value,String unit,String previousValue,String difference,String changeRate,
        String targetValue,String achievementRate,String profitRate,String targetGapPercentagePoints,Integer idlePersonCount,
        Integer overdueCount,String overdueAmount,Basis basis,Link link) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Action(String actionCode,int count,boolean urgent,String amount,String personCount,List<String> months,
        Integer withoutNextAssignmentCount,Integer recommendedPlacementCount,String reason,Basis basis,Link link) {}
    public record Check(String checkCode,String status,int count,String reason) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CloseProgress(Basis basis,String closeStatus,Integer passedCheckCount,int totalCheckCount,List<Check> remainingChecks,boolean snapshot,Link link) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Alert(String projectId,String projectName,String contractTypeCode,String alertType,String reason,String marginRate,String budgetConsumptionRate,Basis basis,Link link) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Trend(String month,String salesAmount,String salesCostAmount,String salesTargetAmount) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Supply(String month,String requiredPersons,String availablePersons,String differencePersons) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Part(String partId,String partName,String salesAmount,String projectCostAmount,String contributionMarginRate) {}
    public record Opportunity(String opportunityId,String opportunityName,String startMonth,String winProbabilityRate,String expectedAmount,Link link) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Pipeline(Basis basis,Integer opportunityCount,String weightedExpectedAmount,List<Opportunity> startingSoon) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Cash(String month,String closingBalance,String minimumCashAmount,Boolean shortage,List<String> unconfirmedReasons,Link link) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Data(Basis actionsBasis,Basis projectAlertsBasis,String selectedMonth,LocalDate today,String currentMonth,
        String cashBaseMonth,String lastClosedMonth,String selectedMonthCloseStatus,boolean provisional,OffsetDateTime observedAt,String scope,
        List<Metric> metrics,List<Action> actions,Integer actionTypeCount,Integer urgentActionTypeCount,CloseProgress closeProgress,List<Alert> projectAlerts,
        Basis salesCostTrendBasis,List<Trend> salesCostTrend,Basis supplyBasis,List<Supply> supplyForecast,Basis salesPartsBasis,List<Part> salesParts,
        String salesPartExplanation,Pipeline pipeline,Basis cashBasis,List<Cash> cashForecast) {}
    public record ReceivableTotals(BigDecimal balance,int overdueCount,BigDecimal overdueAmount) {}
    public record TaxDue(int count) {}
}
