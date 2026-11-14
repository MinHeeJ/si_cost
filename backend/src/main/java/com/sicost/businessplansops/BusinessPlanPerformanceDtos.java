package com.sicost.businessplansops;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class BusinessPlanPerformanceDtos {
    private BusinessPlanPerformanceDtos() {}
    public record Query(int year, String month) {}
    public record YearRange(String fromMonth, String toMonth) {}
    public record Target(String month, BigDecimal sales, BigDecimal profit) {}
    public record Close(String month, String status) {}
    public record Snapshot(String month, String fixed, String migrated, BigDecimal salesAmt,
        BigDecimal allocCostAmt, BigDecimal idleCostAmt, BigDecimal drctExpsAmt, BigDecimal salesCostAmt,
        BigDecimal grossPrftAmt, BigDecimal sgaInputAmt, BigDecimal indrctLaborAmt, BigDecimal rndAmt,
        BigDecimal sgaTotAmt, BigDecimal prevAdjAmt, BigDecimal oprtPrftAmt, BigDecimal noprIncmAmt,
        BigDecimal noprExpsAmt, BigDecimal corpTaxAmt, BigDecimal netPrftAmt) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Context(int year, String month, String baseMonth, String nowMonth, String timezone,
        OffsetDateTime snapshotAt, String aggregation, String amountUnit, boolean readOnly) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Issue(String month, String metric, String code, String reason, boolean estimated) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Source(String month, String sourceTable, String sourceKey, String metric, String amount,
        boolean estimated, String reason) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Completeness(boolean costComplete, boolean revenueComplete, boolean taxComplete,
        List<String> missing, List<String> estimated, List<String> sourceMonths, List<Issue> issues,
        int availableMonthCount, int requestedMonthCount, List<String> missingMonths) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Actual(String month, String sourceKind, String closeStatus, boolean fixed, boolean migrated,
        boolean provisional, Map<String,String> amounts, Completeness completeness, List<Source> sources,
        OffsetDateTime calculatedAt) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Month(String month, String salesTargetAmt, String operatingProfitTargetAmt,
        String salesActualAmt, String operatingProfitActualAmt, String salesAchievementRate,
        String operatingMarginDifference, Boolean salesUnderTarget, Boolean operatingMarginAdverse,
        boolean targetRegistered, Actual actual) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Summary(String metric, String actualAmt, String targetAmt, String achievementRate, Boolean underTarget) {}
    public record Data(Context context, String targetStatus, List<Month> months, List<Summary> summary, List<String> notices) {}
}
