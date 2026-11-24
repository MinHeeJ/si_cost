package com.sicost.candidates;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class RecommendationActionDtos {
    private RecommendationActionDtos() {}
    public record Key(String rcmdId,int rank) { public String auditKey(){return rcmdId+":"+rank;} }
    public record Exclusion(String reasonCode,String expectedRevision) {}
    public record Tentative(String needSn,String expectedRevision,String targetRevision,String candidateRevision,List<String> confirmedWarnings) {}
    public record Row(String rcmdId,int rank,String type,String opportunityId,String needSn,String projectId,String jobCode,
        String startMonth,String developerId,String scoreDetail,String actionCode,String exclusionReasonCode,String actedBy,LocalDateTime actedAt,LocalDateTime registeredAt) {
        public Key key(){return new Key(rcmdId,rank);}
    }
    public record Selection(String actionCode,String exclusionReasonCode,String actedBy,String actedAt,String assignmentId,
        String tentativeAssignmentId,String finalOpportunityId,String finalNeedSn) {}
    public record Result(String rcmdId,int rank,boolean changed,Selection selection,String revision,String message,String releasedTentativeAssignmentId) {}
    public record Warning(String code,String message) {}
    public record Revisions(String expectedRevision,String targetRevision,String candidateRevision) {}
    public record AuditValue(String item,String value) {}
    public record Choice(Key key,String action,String reason,String actor,LocalDateTime now) {}
    public record Slot(String opportunityId,String needSn) {}
    public record MessageContext(String developerId,String jobCode,String previousOpportunityId) {}
    public record MessageNames(String developerName,String jobName,String previousOpportunityName) {}
    public record Need(String opportunityId,String needSn,String jobCode,String startMonth,int months,int count,String status,String contractType,String name) {}
    public record Developer(String id,String jobCode,String employeeType,String directClass,String useYn,LocalDate joinDate,LocalDate retireDate) {}
    public record Period(String developerId,LocalDate startDate,LocalDate endDate) {}
    public record Withdrawal(String id,LocalDate endDate,String extensionPossibility,String status,String projectStatus) {}
}
