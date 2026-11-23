package com.sicost.cashflowops;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
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
        String employeeType,boolean verified,String formula,String sourceLink,List<Issue> issues) {
        @JsonProperty
        public String sourceTypeLabel() {
            return switch(sourceType) {
                case "TB_BL_RCPT" -> "입금";
                case "TB_BL_BILL" -> "입금 예정(청구)";
                case "TB_FI_ITEM" -> "재무 항목";
                case "TB_FI_LOAN" -> "대여금 입출금";
                case "TB_FI_XFER" -> "계좌이체 지출";
                case "TB_FI_CARD_MON" -> "법인카드 대금";
                case "TB_FI_CARD_PAY" -> "법인카드 명세";
                case "TB_FI_TAX_PAY" -> "법인세 납부";
                case "TB_HR_MON_PAY" -> item==Item.EXPENSE ? "급여 공제(4대보험·세금)" : "급여";
                default -> "미확인 원천";
            };
        }
    }
    public record SourcesPage(String month,Item item,List<Source> items,long total,int page,int size,
        boolean hasMore,java.util.Map<String,String> employeeTypeLabels) {}
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
