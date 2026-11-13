package com.sicost.corporatetaxesops;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class CorporateTaxDtos {
    private CorporateTaxDtos() {}
    public record Audit(String regUserId,String regDttm,String modUserId,String modDttm) {}
    public record Annual(String year,String estimatedTax,Audit audit) {}
    public record Payment(String year,String payType,String dueDate,String payAmount,String paidDate,String payYn,
        String displayAmount,String automaticAmount,boolean manuallyEdited,String status,String confirmationRevision,Audit audit) {}
    public record AllocationMonth(String month,String closeStatus,String amount,boolean verified,String fixed,String migrated) {}
    public record Allocation(String year,String estimatedTax,String closedTotal,String remainingAmount,int openMonthCount,
        List<AllocationMonth> months,boolean verified,List<String> warnings) {}
    public record DefaultsResult(boolean changed,Annual corporateTax,List<Payment> taxPayments,String revision) {}
    public record ConfirmationResult(boolean changed,Payment payment,String revision) {}
    public record Confirmation(YearMonth month,String expectedRevision,LocalDate paidDate,BigDecimal payAmount) {}
    public record StoredAnnual(String year,BigDecimal estimatedTax,String regUserId,LocalDateTime regDttm,String modUserId,LocalDateTime modDttm) {}
    public record StoredPayment(String year,String payType,LocalDate dueDate,BigDecimal payAmount,LocalDate paidDate,String payYn,
        String regUserId,LocalDateTime regDttm,String modUserId,LocalDateTime modDttm) {}
    public record StoredMonth(String month,String closeStatus,BigDecimal amount,String fixed,String migrated) {}
    public record Snapshot(StoredAnnual annual,List<StoredPayment> payments) {}
    public record AnnualWrite(String year,BigDecimal estimatedTax,String actor,LocalDateTime now) {}
    public record PaymentWrite(String year,String payType,LocalDate dueDate,String actor,LocalDateTime now) {}
    public record ConfirmationWrite(String year,String payType,LocalDate paidDate,BigDecimal payAmount,String actor,LocalDateTime now) {}
}
