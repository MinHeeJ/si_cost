package com.sicost.salesopportunities;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sicost.salesopportunitiesops.SalesCommandDtos.DraftNeed;
import com.sicost.salesopportunitiesops.SalesCommandDtos.Quote;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class SalesOpportunityDtos {
    private SalesOpportunityDtos() {}
    public record Query(String status,boolean includeStopped,String result,int page,int size,String sort,String direction) {}
    public record Selection(Integer needSn,Integer needIndex,String developerId,String startMonth,String sourceScreenId) {}
    public record Save(String name,String customerId,String partId,String stage,String probability,boolean manualProbability,
        String amount,String startMonth,String contractType,Integer months,String nextTask,LocalDate nextDue,String workplace,
        String memo,List<DraftNeed> needs,List<Selection> tentativeSelections,String expectedRevision) {}
    public record Stored(String id,String name,String customerId,String partId,String stage,BigDecimal probability,
        String manualProbability,BigDecimal amount,String startMonth,String contractType,Integer months,String nextTask,
        LocalDate nextDue,String workplace,String memo,String status,BigDecimal profitRate,String approvalStatus,
        BigDecimal approvedProfitRate,String projectId,LocalDate resultDate,String lossReasonCode,String lossMemo,
        String registeredBy,LocalDateTime registeredAt,String modifiedBy,LocalDateTime modifiedAt) {}
    public record StoredTent(String id,String opportunityId,int needSn,String developerId,String startMonth,String status,
        String sourceScreenId,LocalDateTime processedAt,String registeredBy,LocalDateTime registeredAt,String modifiedBy,LocalDateTime modifiedAt) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Tentative(String id,String opportunityId,int needSn,String developerId,String startMonth,String status,
        String sourceScreenId,LocalDateTime processedAt) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ApprovalHistory(int histSn,String action,String profitRate,String reason,String registeredBy,LocalDateTime registeredAt) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Opportunity(String id,String name,String customerId,String partId,String stage,String probability,
        boolean manualProbability,String amount,String startMonth,String contractType,Integer months,String nextTask,
        LocalDate nextDue,String workplace,String memo,String status,List<DraftNeed> needs,List<Tentative> tentativeAssignments,
        List<ApprovalHistory> approvalHistory,Quote quote,String approvalStatus,String approvedProfitRate,String projectId,
        LocalDate resultDate,String lossReasonCode,String lossMemo,String revision,LocalDateTime modifiedAt,LocalDateTime registeredAt) {}
    public record Summary(int opportunityCount,int staleCount,String amount,String weightedAmount,String weightedPeople) {}
    public record StageTotal(String stage,int count,String amount) {}
    public record Page(List<Opportunity> items,int total,int page,int size,Summary summary,List<StageTotal> stageTotals) {}
    public record Write(String id,Save input,BigDecimal rate,String approval,BigDecimal approved,String actor,LocalDateTime now) {}
    public record NeedWrite(String id,DraftNeed need,String actor,LocalDateTime now) {}
    public record NeedKey(String id,int needSn) {}
    public record TentWrite(StoredTent value,String actor,LocalDateTime now) {}
    public record CodeKey(String group,String code) {}
    public record Field(String tableName,String targetKey,String item,String value) {}
}
