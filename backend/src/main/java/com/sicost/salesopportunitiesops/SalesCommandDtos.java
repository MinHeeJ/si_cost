package com.sicost.salesopportunitiesops;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class SalesCommandDtos {
    private SalesCommandDtos() {}
    public record DraftNeed(Integer needSn,String jobCode,String gradeCode,Integer count,String startMonth,
                            Integer months,String monthlyMm,String expectedRate) {}
    public record Preview(List<DraftNeed> needs) {}
    public record Stage(String expectedRevision,String stage,String probability,Boolean manualProbability) {}
    public record Approval(String expectedRevision,String action,String reason) {}
    public record Loss(String expectedRevision,String reasonCode,String memo) {}
    public record Revision(String expectedRevision) {}
    public record Win(String expectedRevision,String contractAmount,String contractType,LocalDate startDate,
                      LocalDate endDate,String projectStatus) {}
    public record SaveResult(boolean changed,String revision,List<String> warnings,String opportunityId) {}
    public record WinResult(boolean changed,String revision,List<String> warnings,String opportunityId,String projectId) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Issue(String field,Integer needSn,String reason,String status) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record QuoteRow(Integer needSn,String jobCode,String gradeCode,String quantityMm,String effectiveRate,
        String unitMonthlyCost,String revenue,String cost,String minimumRate,String targetRate,String costBasisMonth,
        String cohort,int cohortCount,boolean fallbackUsed,boolean complete,String unknownReason) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Quote(List<QuoteRow> rows,List<Issue> issues,String revenue,String cost,String rawProfitRate,
        String storedProfitRate,String minimumAmount,String targetAmount,String targetUnavailableReason,
        String judgement,String standardMonth,String approvalThreshold,String reapprovalGap,String targetMargin,String formula) {}
    public record Standard(String code,BigDecimal value) {}
    public record Code(String group,String code,String defaultRate) {}
    public record PersonCost(String id,String jobCode,String gradeCode,BigDecimal cost) {}
    public record QuoteSources(String month,String costMonth,List<Standard> standards,List<Code> codes,List<PersonCost> people) {}
    public record Opportunity(String id,String name,String customerId,String partId,String stage,BigDecimal probability,
        String manualProbability,BigDecimal amount,String startMonth,String contractType,Integer months,String workplace,
        String memo,String status,BigDecimal profitRate,String approvalStatus,BigDecimal approvedProfitRate,
        LocalDate resultDate,String lossReasonCode,String lossMemo,String projectId,String registeredBy) {}
    public record Tentative(String id,String developerId,String status,LocalDateTime processedAt) {}
    public record Mutation(Opportunity value,String actor,LocalDateTime now) {}
    public record TentativeMutation(String id,String opportunityId,String status,String actor,LocalDateTime now) {}
    public record ApprovalEvent(String id,int serial,String action,BigDecimal profitRate,String reason,String actor,LocalDateTime now) {}
    public record Notification(String id,String recipient,String content,String actor,LocalDateTime now) {}
    public record Plan(String projectId,String jobCode,BigDecimal planMm,String actor,LocalDateTime now) {}
}
