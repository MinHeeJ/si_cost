package com.sicost.apiops;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class BillingReviewDtos {
    private BillingReviewDtos() {}
    public record PageQuery(String month, int page, int size) {}
    public record Project(String id, String name, String contractType, String status) {}
    public record AssignmentReference(String id, String developerName, String managementNo) {}
    public record CheckKey(String projectId, String month) {}
    public record CheckContext(String projectId, String month, String checkRevision) {}
    public record BillingSource(String assignmentId, String developerId, String developerName, String managementNo,
                                String month, String appliedUnitPrice, String mm, String supplyAmount, boolean protectedSource) {
        @com.fasterxml.jackson.annotation.JsonProperty("protected") public boolean protectedSource() { return protectedSource; }
    }
    public record CheckBill(String id, LocalDate billingDate, BigDecimal supplyAmount, BigDecimal vatRate,
                            BigDecimal receiptAmount) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Remedy(String mode, String projectId, String month, String billId, String proposedSupplyAmount,
                         String expectedRevision, CheckContext checkContext, String reason, List<String> relatedBillIds) {}
    public record CheckRow(String projectId, String projectName, String contractType, int personCount, String mm,
                           String estimatedAmount, String billedAmount, String differenceAmount, String tolerance,
                           int billCount, String verdict, String explanation, List<BillingSource> sources,
                           Remedy remedy, String checkRevision) {}
    public record ChecksSummary(int projectCount, int matchedCount, int missingCount, int differenceCount,
                                String missingSupplyAmount, int tabCount) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ChecksPage(String month, List<String> availableMonths, String defaultMonth, boolean includesPlanned,
                             ChecksSummary summary, List<CheckRow> items, int total, int page, int size, List<String> warnings) {}
    public record OverdueBill(String billId, String projectId, String projectName, BigDecimal unpaidAmount, LocalDate dueDate) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Dunning(int elapsedDays, int step, Integer nextStep, LocalDate nextDate, boolean finalStep) {}
    public record Recipient(String userId, String name, List<String> roles) {}
    public record RecipientGroup(int step, List<Recipient> recipients, List<String> unassignedReasons) {}
    public record RecipientRole(String userId, String name, String role) {}
    public record ProjectPart(String partId, boolean headquarters, String leaderId) {}
    public record RecipientScope(String userId, String type, String partId, String projectId) {}
    public record DunningHistory(String billId, int sn, int step, LocalDate sendDate, String receiverDescription,
                                  String sendMethodCode, boolean resend) {}
    public record Receivable(String billId, String projectId, String projectName, String unpaidAmount, LocalDate dueDate,
                              Dunning dunning, List<RecipientGroup> recipients, List<DunningHistory> sentHistory, String sendNotice) {}
    public record StepCard(int step, String thresholdDays, int count, List<String> recipientRoles) {}
    public record ReceivablesPage(LocalDate asOfDate, List<Receivable> items, int total, int tabCount,
                                  int page, int size, List<StepCard> cards) {}
}
