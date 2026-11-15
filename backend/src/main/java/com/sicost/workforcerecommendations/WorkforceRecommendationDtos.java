package com.sicost.workforcerecommendations;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class WorkforceRecommendationDtos {
    private WorkforceRecommendationDtos() {}
    public record Condition(String targetType,String targetId,String jobCode,String startMonth,String requestedGradeCode,String needSn) {}
    public record Developer(String developerId,String name,String managementNumber,String jobCode,String subJobCode,String gradeCode,String employeeTypeCode,String detailId) {}
    public record ScoreItem(String itemCode,String baseScore,String weight,String score,String basis) {}
    public record Evidence(String j,List<Integer> sk,String cu,Integer g,List<Integer> p,String a,String l) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AiScoreDetail(int v,List<BigDecimal> b,List<BigDecimal> w,List<BigDecimal> s,Evidence e,String m,String c,String u,int f) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Selection(String actionCode,String exclusionReasonCode,String actedBy,String actedAt,String assignmentId,String tentativeAssignmentId,String finalOpportunityId,String finalNeedSn) {}
    public record OtherTentative(String tentativeAssignmentId,String opportunityId,String opportunityName,String needSn,String startMonth) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Candidate(int rank,Developer developer,String opportunityId,String needSn,String jobCode,String startMonth,String requestedGradeCode,
        String totalScore,List<ScoreItem> scoreItems,com.fasterxml.jackson.databind.JsonNode scoreDetail,String reason,List<OtherTentative> otherTentativeAssignments,
        Selection selection,String revision,String targetRevision,String candidateRevision,com.fasterxml.jackson.databind.JsonNode nextPosition) {}
    public record ReasonCount(String reason,int count) {}
    public record Diagnostic(String developerId,List<String> reasons) {}
    public record Exclusions(int totalCandidateCount,int excludedUniqueCount,List<ReasonCount> byReason,List<Diagnostic> diagnostics,boolean reasonCountsOverlap) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Data(String rcmdId,String recommendationType,Condition appliedCondition,String sourceAssignmentId,String executedAt,String executedBy,
        List<Candidate> candidates,Exclusions exclusions,String message,String revision,List<Object> shortageMonths) {}
    public record Target(String targetId,String contractType,String customerId,String industry,String address,String feature,LocalDate startDate,LocalDate endDate,String status) {}
    public record Person(String developerId,String name,String managementNumber,String jobCode,String subJobCode,String gradeCode,String employeeTypeCode,
        String useYn,String directClass,String workStatus,LocalDate hireDate,LocalDate retireDate,String areaCode,String address) {
        public Developer developer(){return new Developer(developerId,name,managementNumber,jobCode,subJobCode,gradeCode,employeeTypeCode,developerId);}
    }
    public record Code(String groupId,String code,String name,int order,String related) {}
    public record Standard(String code,BigDecimal value) {}
    public record Skill(String developerId,String name) {}
    public record Leave(String developerId,LocalDate startDate,LocalDate endDate) {}
    public record Area(String code,String name,String fullName) {}
    public record Assignment(String assignmentId,String developerId,String customerId,String industry,String projectStatus,String contractType,boolean hrEarly) {}
    public record Tentative(String developerId,String tentativeAssignmentId,String opportunityId,String opportunityName,String needSn,String startMonth) {}
    public record Excluded(String developerId) {}
    public record AuditValue(String item,String value) {}
    public record NextCard(String opportunityName,String salesStepCode,String customerId,String customerName,boolean alreadyTentativelyAssigned) {}
    public record Row(String rcmdId,int rank,String recommendationType,String opportunityId,String needSn,String projectId,String jobCode,String startMonth,
        String requestedGradeCode,String developerId,String totalScore,String scoreDetail,String reason,String actionCode,String exclusionReasonCode,
        String actedBy,LocalDateTime actedAt,String executedBy,LocalDateTime executedAt) {}
    public record Write(Row row) {}
    public record Calculation(Person person,AiScoreDetail detail,String totalScore,String reason) {}
}
