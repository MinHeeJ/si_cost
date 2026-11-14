package com.sicost.apiops;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class FinancialReadDtos {
    private FinancialReadDtos() {}
    public record Period(String month,String databaseMonth,String year,String priorYear,LocalDate start,LocalDate end) {}
    public record HistoryQuery(Period period,String scope,int page,int size,long offset,String uploadId,String partId) {
        public HistoryQuery(Period period,String scope,int page,int size,long offset,String uploadId) {
            this(period,scope,page,size,offset,uploadId,null);
        }
        public HistoryQuery(Period period,String scope,int page,int size,long offset) {
            this(period,scope,page,size,offset,null);
        }
    }
    public record SourceTotals(BigDecimal sgaAmount,BigDecimal sgaVatAmount,BigDecimal nonOperatingIncome,
        BigDecimal nonOperatingIncomeVat,BigDecimal nonOperatingExpense,BigDecimal nonOperatingExpenseVat,
        BigDecimal adjustmentProfit,BigDecimal cashFinancialExpense,BigDecimal cashMiscIncome,
        BigDecimal cashCardExpense,BigDecimal cashTransferExpense,BigDecimal cashUnpaidLabor,
        BigDecimal transferTotal,BigDecimal cashLoanIn,BigDecimal cashLoanOut,BigDecimal cashTaxPayment) {}
    public record TaxMonth(String month,String closeStatus,BigDecimal amount) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Contributions(String sgaAmount,String sgaVatAmount,String nonOperatingIncome,
        String nonOperatingIncomeVat,String nonOperatingExpense,String nonOperatingExpenseVat,
        String adjustmentProfit,String cashFinancialExpense,String cashMiscIncome,String cashCardExpense,
        String cashTransferExpense,String cashUnpaidLabor,String transferTotal,String cashLoanIn,
        String cashLoanOut,String cashLoanNet,String month,String corporateTaxAllocation,String cashTaxPayment,
        boolean verified,List<String> warnings) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record HistoryEntry(String historyId,String targetTable,String targetKey,String changeType,
        String field,String beforeValue,String afterValue,String reason,String userId,String occurredAt) {}
    public record HistoryPage(List<HistoryEntry> items,long total,int page,int size) {}
}
