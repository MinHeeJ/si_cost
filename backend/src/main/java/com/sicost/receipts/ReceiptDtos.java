package com.sicost.receipts;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ReceiptDtos {
    private ReceiptDtos() {}
    public record CreateReceipt(Integer year,String expectedRevision,LocalDate date,BigDecimal amount) {}
    public record CancelReceipt(Integer year,String expectedRevision,String reason) {}
    public record StoredBill(String id,String projectId,String projectName,String customerName,String contractType,
        int order,String month,LocalDate billingDate,BigDecimal supplyAmount,BigDecimal vatRate,BigDecimal vatAmount,
        BigDecimal totalAmount,LocalDate dueDate,String taxInvoiceNo,String cancelled,String cancelReason,
        String cancelUserId,LocalDateTime cancelledAt) {}
    public record StoredReceipt(long id,String billId,LocalDate date,BigDecimal amount,String cancelled,
        String cancelReason,String cancelUserId,LocalDateTime cancelledAt) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record BillReceipt(long id,String billId,String date,String amount,boolean cancelled,
        String cancelReason,String cancelUserId,String cancelledAt) {}
    public record BillReceipts(List<BillReceipt> items,int total,int normalCount,String revision) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Bill(String id,String projectId,String projectName,String customerName,String contractType,
        int order,String billingMonth,String billingDate,String supplyAmount,String vatRate,String vatAmount,
        String totalAmount,String receiptAmount,String unpaidAmount,String dueDate,String taxInvoiceNo,
        String taxInvoiceWarning,String status,boolean cancelled,String cancelReason,String cancelUserId,
        String cancelledAt,boolean carryover,Object dunning,QuickActions quickActions,String revision) {}
    public record QuickActions(boolean copyAllowed,boolean nextMonthAllowed,String reason) {}
    public record QuickSource(String status,LocalDate endDate,boolean latest,boolean targetExists) {}
    public record Dunning(int elapsedDays,int step,Integer nextStep,String nextDate,boolean finalStep) {}
    public record BillReceiptResult(boolean changed,BillReceipt receipt,Bill bill,String revision) {}
    public record ReceiptWrite(String billId,LocalDate date,BigDecimal amount,String actor,LocalDateTime now) {}
    public record Cancellation(String billId,long receiptId,String reason,String actor,LocalDateTime now) {}
    public record CacheWrite(String billId,BigDecimal receiptAmount,BigDecimal unpaidAmount,String actor,LocalDateTime now) {}
    public record Cache(BigDecimal receiptAmount,BigDecimal unpaidAmount) {}
}
