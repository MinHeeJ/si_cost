package com.sicost.receiptsops;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ReceiptBatchDtos {
    private ReceiptBatchDtos() {}
    public record Item(String billId, String amount, String expectedRevision) {}
    public record Request(Integer year, LocalDate date, List<Item> items) {}
    public record BillState(String id, String projectId, String month, BigDecimal total,
                            BigDecimal receiptAmount, String cancelled) {
        public BigDecimal unpaid() { return total.subtract(receiptAmount); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Receipt(long id, String billId, String date, String amount, boolean cancelled,
                          String cancelReason, String cancelUserId, String cancelledAt) {}
    public record ReceiptResult(boolean changed, Receipt receipt, Map<String,Object> bill, String revision) {}
    public record Result(List<ReceiptResult> items, int completedCount, String totalAmount) {}
    public record CacheWrite(String billId, BigDecimal receiptAmount, BigDecimal unpaidAmount,
                             String actor, LocalDateTime now) {}
    public static final class ReceiptWrite {
        private Long id;
        private final String billId;
        private final LocalDate date;
        private final BigDecimal amount;
        private final String actor;
        private final LocalDateTime now;
        public ReceiptWrite(String billId, LocalDate date, BigDecimal amount, String actor, LocalDateTime now) {
            this.billId=billId;this.date=date;this.amount=amount;this.actor=actor;this.now=now;
        }
        public Long getId() { return id; }
        public void setId(Long id) { this.id=id; }
        public String getBillId() { return billId; }
        public LocalDate getDate() { return date; }
        public BigDecimal getAmount() { return amount; }
        public String getActor() { return actor; }
        public LocalDateTime getNow() { return now; }
    }
}
