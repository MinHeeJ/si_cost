package com.sicost.monthclosesops;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public final class MonthCloseDtos {
    private MonthCloseDtos() {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Check(String code,String name,String status,Long count,String description,String checkedAt,
        String linkScreenId,String linkMonth,String linkFilter) {}
    public record Issue(String code,String message,String month,String sourceType,String sourceId,String field) {}
    public record SnapshotCount(String table,int rowCount,int preservedIndependentFixedCount) {}
    public record Part(String partId,String partName,String status,boolean willAutoConfirm,List<Issue> issues) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CashImpact(String excludedUnpaidBills,String excludedUnconfirmedTaxes,String incomeBefore,String incomeAfter,
        String taxPaymentBefore,String taxPaymentAfter,String closingBalanceBefore,String closingBalanceAfter) {}
    public record Preview(String month,String revision,boolean canClose,List<Check> checks,int warningCount,List<String> remainingCheckNames,
        List<Part> parts,int eligiblePartCount,int autoConfirmCount,List<SnapshotCount> snapshots,boolean usesEstimatedPayroll,
        CashImpact cashImpact,List<Issue> blockingIssues) {}
    public record Adjustment(String adjustmentId,String targetMonth,String applyMonth,String amount,String reason) {}
    public record ReopenPreview(String month,String revision,boolean canReopen,String closedBy,String closedAt,
        List<SnapshotCount> snapshots,boolean settlementsPreserved,List<Adjustment> adjustments,int adjustmentCount,
        String adjustmentAmount,List<String> adjustmentMonths,List<Issue> blockingIssues) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record History(String month,int historySequence,String action,String actorId,String occurredAt,String reason,String remainingChecks) {}
    public record Commit(String month,String status,String revision,String operationId,History history,boolean replayed,
        List<SnapshotCount> snapshots,boolean settlementsPreserved,List<String> invalidatedQueries) {}
    public record ChecksSaved(String month,String status,String revision,List<Check> checks,String checkedAt) {}
}
