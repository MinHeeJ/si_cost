package com.sicost.forecast;

import java.util.List;

public final class SalesForecastDtos {
    private SalesForecastDtos() {}
    public record Issue(String field, Integer needSn, String reason, String status) {}
    public record RevenueMonth(String month, String projectRevenue, String opportunityRevenue, String totalRevenue, String allWonRevenue) {}
    public record RevenueSource(String sourceType, String sourceId, String name, String contractType, String month,
                                String amount, String allWonAmount, String basis) {}
    public record Summary(String sixMonthRevenue, String opportunityRevenue, String allWonRevenue,
                          String averageDemand, String averageSupply, int hiringJobCount) {}
    public record WorkforceMonth(String month, String supply, String opportunityDemand, String unfilledDemand, String demand,
        String difference, String rawSupply, String rawOpportunityDemand, String rawUnfilledDemand, String status) {}
    public record JobMonth(String jobCode, WorkforceMonth month, String basis) {}
    public record Hiring(String jobCode, String firstShortageMonth, String maximumShortage,
                         String recommendedStartMonth, boolean startNow, int candidateCount) {}
    public record Workforce(String from, int months, boolean includeUnfilled, String basisDate, String standardMonth,
        List<WorkforceMonth> totals, List<JobMonth> jobDiagnostics, List<Hiring> hiring, List<Issue> issues,
        String lastMonthSupply, String lastMonthDemand, String mostShortageJobCode, String maximumShortage) {}
    public record Result(String from, int months, Summary summary, List<RevenueMonth> revenueMonths,
                         List<RevenueSource> revenueSources, Workforce workforce) {}
}
