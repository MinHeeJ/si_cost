package com.sicost.structure;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sicost.businessplansops.BusinessPlanPerformanceDtos.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class ProfitStructureDtos {
    private ProfitStructureDtos() {}
    public record Query(int year,String month,String aggregation,String amountUnit) {}
    public record ProjectCustomer(String projectId,String customerId,String customerName) {}
    public record ProjectAmount(String month,String projectId,BigDecimal amount) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Comparison(String metric,List<String> months,List<String> comparisonMonths,String currentValue,
        String comparisonValue,String changeRate,String percentagePointChange,boolean comparable,
        int availableMonthCount,int requestedMonthCount,List<String> missingMonths,String direction) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Card(String metric,String amount,String rate,Comparison previous,Comparison previousYear) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Group(String groupKind,String groupId,String groupName,boolean other,String salesAmt,
        String allocatedLaborCostAmt,String contributionAmt,String revenueShare,String contributionRate,
        String positiveContributionShare,boolean loss,boolean showBar) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Composition(boolean visible,String reason,boolean complete,String revenueDifference,
        List<Group> contractTypes,List<Group> customers,List<Group> salesParts) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Reference(boolean visible,String amount,boolean complete,String reason,List<Issue> issues) {}
    public record Bucket(String key,List<String> months,String throughMonth,int monthCount,Map<String,String> amounts,
        Completeness completeness,boolean provisional,List<Comparison> comparisons,Composition composition,Reference referenceRevenue) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Waterfall(String metric,String label,String kind,String amount,boolean connected) {}
    public record NumericRow(String metric,String label,boolean isRate,List<String> bucketKeys,List<String> bucketValues) {}
    public record Method(String metric,List<String> sources,String formula,boolean referenceOnly,boolean vatExcluded) {}
    public record Data(Context context,List<Actual> months,List<Bucket> buckets,List<Card> summary,
        List<Waterfall> waterfall,List<NumericRow> numericRows,List<Method> calculationMethods,List<String> notices) {}
}
