package com.sicost.salespartsettlements;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class SalesPartSettlementDtos {
    private SalesPartSettlementDtos() {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Issue(String code, String message, String field, String developerId, String assignmentId,
        String projectId, String month, String source, boolean blocking) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Standard(String leaderId, String leaderName, String distributionRate, String salesExpenseAmount,
        String effectiveMonth, boolean valid) {}
    public record Tab(String partId, String partName, boolean eligible, boolean historyOnly, String status,
        Standard standard, List<Issue> issues) {}
    public record TabsResult(String month, String baseMonth, String currentMonth, List<Tab> parts,
        int eligibleCount, int confirmedCount, int unconfirmedCount, List<Issue> issues) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Basis(String paySource, boolean estimated, boolean firstMonthMissingPay, LocalDate firstInputDate,
        Integer calendarDays, Integer payableDays, String basePayAmount, String distributionRate,
        List<String> tkSources, List<Issue> warnings) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Row(String developerId, String name, String managementNo, String employeeType,
        List<String> projectNames, String legacyProjectText, String mm, String unitPrice, String payAmount,
        String salesExpenseAmount, String retirementAmount, String costAmount, String profitAmount,
        String profitShareAmount, String remark, Basis basis, List<Issue> issues) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Totals(int personCount, int deficitPersonCount, String mm, String unitPrice, String payAmount,
        String salesExpenseAmount, String retirementAmount, String costAmount, String profitAmount,
        String personProfitShareAmount, String cardAmount, String partProfitAmount) {}
    public record Page(int page, int size, long total) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Result(String partId, String month, String status, Standard standard, String cardAmount,
        String revision, String confirmedBy, String confirmedByName, String confirmedAt, String lastCancellationReason,
        boolean readOnly, List<String> readOnlyReasons, boolean canSaveCard, boolean canConfirm, boolean canCancel,
        Totals summary, Totals totals, List<Row> items, Page pagination, List<Issue> issues) {}
    public record PartRow(String partId, String name, String headquarters, String useYn,
        String regUserId, LocalDateTime regDttm, String modUserId, LocalDateTime modDttm) {}
    public record StandardRow(String partId, String month, String endMonth, String leaderId, String leaderName,
        BigDecimal rate, BigDecimal expense, String regUserId, LocalDateTime regDttm, String modUserId, LocalDateTime modDttm) {}
    public record UseHistory(String partId, String histId, String beforeValue, String afterValue,
        LocalDateTime at) {}
    public record Parent(String partId, String month, BigDecimal cardAmount, BigDecimal partProfitAmount,
        String status, String confirmedBy, String confirmedByName, LocalDateTime confirmedAt, String cancellationReason,
        String regUserId, LocalDateTime regDttm, String modUserId, LocalDateTime modDttm) {
        public boolean confirmed() { return "CNFM".equals(status) || "AUTO".equals(status); }
    }
    public record Detail(String partId, String month, String developerId, String name, String managementNo,
        String employeeType, String projectText, BigDecimal mm, BigDecimal unitPrice, BigDecimal pay,
        BigDecimal expense, BigDecimal retirement, BigDecimal cost, BigDecimal profit, BigDecimal share, String remark,
        String regUserId, LocalDateTime regDttm, String modUserId, LocalDateTime modDttm) {}
    public record FirstInput(String developerId, LocalDate date) {}
    public record RevisionRow(String source, String storedRows) {}
    public static Issue issue(String code, String message, String field, String developerId, String month, String source, boolean blocking) {
        return new Issue(code, message, field, developerId, null, null, month, source, blocking);
    }
}
