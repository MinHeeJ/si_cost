package com.sicost.billsops;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class BillOperationsDtos {
    private BillOperationsDtos() {}
    public record Project(String id,String name,String customerName,String contractType,String status,LocalDate endDate) {}
    public record StoredBill(String id,String projectId,int order,String month,LocalDate billingDate,BigDecimal supply,
        BigDecimal rate,BigDecimal vat,BigDecimal total,BigDecimal receiptCache,BigDecimal unpaidCache,LocalDate dueDate,
        String invoice,String cancelled,String cancelReason,String cancelUserId,LocalDateTime cancelledAt,
        String registeredBy,LocalDateTime registeredAt,String modifiedBy,LocalDateTime modifiedAt) {}
    public record Receipt(long id,String billId,LocalDate date,BigDecimal amount,String cancelled,String reason,
        String cancelUserId,LocalDateTime cancelledAt,String registeredBy,LocalDateTime registeredAt,String modifiedBy,LocalDateTime modifiedAt) {}
    public record Snapshot(StoredBill bill,List<Receipt> receipts) {}
    public record Developer(String id,String name,String managementNo) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record BillingSource(String assignmentId,String developerId,String developerName,String managementNo,String month,
        String appliedUnitPrice,String mm,String supplyAmount,boolean protectedMonth) {
        @com.fasterxml.jackson.annotation.JsonProperty("protected") public boolean protectedMonth() {return protectedMonth;}
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record BillEstimate(String projectId,String month,String supplyAmount,String vatRate,LocalDate defaultBillingDate,
        LocalDate defaultDueDate,List<BillingSource> sources,List<String> warnings) {}
    public record BillingSummaryCard(String amount,int count,String scope) {}
    public record BillsSummary(int year,boolean includeCarryover,BillingSummaryCard billed,BillingSummaryCard currentMonth,
        BillingSummaryCard unpaid,int lateCount,String lateScope) {}
    public record BillSelectionItem(String billId,String unpaidAmount,String expectedRevision) {}
    public record BillSelectionExcluded(String billId,String reason) {}
    public record BillSelection(List<BillSelectionItem> items,int total,List<BillSelectionExcluded> excluded,int excludedCount,boolean canSave) {}
    public record SummaryQuery(int year,boolean includeCarryover) {}
    public record SelectionQuery(int year,String status,String month,String projectId,boolean includeCarryover,
        String sort,String direction,List<String> ids,List<String> excludeIds) {}
    public record MutationRequest(Integer year,String expectedRevision,String reason) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record BillCopyDraft(String sourceBillId,String expectedRevision,int year,String projectId,String billingMonth,
        String supplyAmount,String vatRate,LocalDate billingDate,LocalDate dueDate,String taxInvoiceNo,List<String> warnings) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record QuickActions(boolean copyAllowed,boolean nextMonthAllowed,String reason) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Dunning(int elapsedDays,int step,Integer nextStep,LocalDate nextDate,boolean finalStep) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Bill(String id,String projectId,String projectName,String customerName,String contractType,int order,
        String billingMonth,LocalDate billingDate,String supplyAmount,String vatRate,String vatAmount,String totalAmount,
        String receiptAmount,String unpaidAmount,LocalDate dueDate,String taxInvoiceNo,String taxInvoiceWarning,String status,
        boolean cancelled,String cancelReason,String cancelUserId,String cancelledAt,boolean carryover,Dunning dunning,
        QuickActions quickActions,String revision) {}
    public record BillMutationResult(boolean changed,Bill bill,String revision,List<String> warnings) {}
    public record Cancellation(String id,String reason,String actor,LocalDateTime now) {}
    public record NewBill(String id,String projectId,int order,String month,LocalDate billingDate,BigDecimal supply,
        BigDecimal rate,BigDecimal vat,BigDecimal total,LocalDate dueDate,String actor,LocalDateTime now) {}
}
