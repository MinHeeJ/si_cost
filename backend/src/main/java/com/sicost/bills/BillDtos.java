package com.sicost.bills;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class BillDtos {
    private BillDtos() {}
    public record Query(int year,String status,String month,String projectId,boolean includeCarryover,int page,int size,String sort,String direction) {}
    public record Filter(Query query,int currentYear,LocalDate today) {}
    public record Editable(BigDecimal supplyAmount,BigDecimal vatRate,LocalDate billingDate,LocalDate dueDate,String taxInvoiceNo) {}
    public record CheckContext(String projectId,String month,String checkRevision) {}
    public record CopyContext(String sourceBillId,String expectedRevision) {}
    public record CreateBillRequest(int year,String projectId,String billingMonth,Editable fields,boolean confirmDuplicate,String duplicateToken,CheckContext checkContext,CopyContext copyContext) {}
    public record PutBillRequest(int year,String expectedRevision,Editable fields,CheckContext checkContext) {}
    public record Project(String id,String name,String customerName,String contractType,String status,LocalDate endDate) {}
    public record Stored(String id,String projectId,int order,String billingMonth,LocalDate billingDate,BigDecimal supplyAmount,
        BigDecimal vatRate,BigDecimal vatAmount,BigDecimal totalAmount,BigDecimal receiptAmount,BigDecimal unpaidAmount,
        LocalDate dueDate,String taxInvoiceNo,boolean cancelled,String cancelReason,String cancelUserId,LocalDateTime cancelledAt,
        String regUserId,LocalDateTime registeredAt,String modUserId,LocalDateTime modifiedAt) {}
    public record ReceiptStored(long id,String billId,LocalDate date,BigDecimal amount,boolean cancelled,String cancelReason,
        String cancelUserId,LocalDateTime cancelledAt,String regUserId,LocalDateTime registeredAt,String modUserId,LocalDateTime modifiedAt) {}
    public record Snapshot(Stored bill,List<ReceiptStored> receipts) {}
    public record Write(String id,String projectId,int order,String month,Editable fields,BigDecimal vat,BigDecimal total,BigDecimal receipts,String actor,LocalDateTime now) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Dunning(int elapsedDays,int step,Integer nextStep,String nextDate,boolean finalStep) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record QuickActions(boolean copyAllowed,boolean nextMonthAllowed,String reason) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Bill(String id,String projectId,String projectName,String customerName,String contractType,int order,String billingMonth,
        String billingDate,String supplyAmount,String vatRate,String vatAmount,String totalAmount,String receiptAmount,String unpaidAmount,
        String dueDate,String taxInvoiceNo,String taxInvoiceWarning,String status,boolean cancelled,String cancelReason,String cancelUserId,
        String cancelledAt,boolean carryover,Dunning dunning,QuickActions quickActions,String revision) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Receipt(long id,String billId,String date,String amount,boolean cancelled,String cancelReason,String cancelUserId,String cancelledAt) {}
    public record Receipts(List<Receipt> items,int total,int normalCount,String revision) {}
    public record BillDetail(Bill bill,Receipts receipts,String revision) {}
    public record Totals(String supplyAmount,String vatAmount,String totalAmount,String receiptAmount,String unpaidAmount) {}
    public record BillsPage(List<Bill> items,int total,int page,int size,Totals totals) {}
    public record BillMutationResult(boolean changed,Bill bill,String revision,List<String> warnings) {}
    public record ExistingBill(String id,int order) {}
    public record DuplicateDraft(int year,String projectId,String month,Editable fields,CopyContext copyContext) {}
    public record DuplicateSnapshot(DuplicateDraft draft,List<Snapshot> bills) {}
    public record CheckSnapshot(String projectId,String month,String sources,List<Snapshot> bills) {}
}
