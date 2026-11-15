package com.sicost.cashflowops;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.util.List;

public final class CashFlowDtos {
    private CashFlowDtos() {}
    public enum Item { CASH_SALES, MISC, LOAN, REG_PAY, CON_FL_PAY, OS_PAY, UNPAID_LABOR, EXPENSE, CORP_TAX }
    public enum Unit {
        WON(0,"원"), THOUSAND_WON(3,"천원"), MILLION_WON(6,"백만원");
        public final int scale; public final String label;
        Unit(int scale,String label){this.scale=scale;this.label=label;}
    }
    public record Query(Integer year,Unit unit) {}
    public record SourceQuery(String month,Item item,int page,int size) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Issue(String code,String message,String month,String sourceType,String sourceId,String field) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Source(String sourceType,String sourceId,Item item,@JsonFormat(shape=JsonFormat.Shape.STRING) LocalDate attributionDate,String sourceMonth,
        String attributionMonth,String status,String amount,String vatAmount,boolean includesVat,String referencePayrollMonth,
        String employeeType,boolean verified,String formula,String sourceLink,List<Issue> issues) {}
    public record SourcesPage(String month,Item item,List<Source> items,long total,int page,int size,boolean hasMore) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ItemAmount(Item item,String amount,boolean includesVat,boolean verified) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Month(String month,String status,String openingBalance,List<ItemAmount> items,String incomeTotal,
        String expenseTotal,String closingBalance,boolean verified,boolean shortage,List<Issue> issues) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PeriodTotal(String period,String openingBalance,List<ItemAmount> items,String incomeTotal,String expenseTotal,String closingBalance) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ChartPoint(String month,String balance,String status,boolean shortage) {}
    public record Result(int year,Unit unit,@JsonFormat(shape=JsonFormat.Shape.STRING) LocalDate asOfDate,String timezone,String baseMonth,String baseAmount,
        String minimumCash,String minimumCashMonth,List<Month> months,List<PeriodTotal> periodTotals,
        List<ChartPoint> chartPoints,List<ChartPoint> shortages,List<Issue> issues) {}
}
