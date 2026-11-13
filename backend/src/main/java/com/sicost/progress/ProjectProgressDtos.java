package com.sicost.progress;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class ProjectProgressDtos {
    private ProjectProgressDtos() {}
    public record ProjectRow(String projectId, String projectName, String contractType, String sourceStatus,
        LocalDate startDate, LocalDate endDate, BigDecimal budget, LocalDateTime registeredAt,
        String partId, String partName, boolean hq) {}
    public record Actor(String userId, String userName) {}
    public record StepRow(String projectId, int stepSn, String stepCode, String stepName, BigDecimal weight,
        LocalDate startDate, LocalDate endDate, LocalDate completedDate, String registeredBy, String registeredName,
        LocalDateTime registeredAt, String modifiedBy, String modifiedName, LocalDateTime modifiedAt) {}
    public record MemoRow(String projectId, int memoSn, LocalDate writtenDate, String thisWeek, String nextWeek,
        String request, Integer actualProgress, String registeredBy, String registeredName, LocalDateTime registeredAt,
        String modifiedBy, String modifiedName, LocalDateTime modifiedAt) {}
    public record IssueRow(long issueId, String projectId, String projectName, String title, String importance,
        String importanceName, LocalDate dueDate, String status, String statusName, String action,
        String registeredBy, String registeredName, LocalDateTime registeredAt, String modifiedBy,
        String modifiedName, LocalDateTime modifiedAt) {}
    public record Template(String code, String name, String weight, String types, String useYn, String delYn, int sortOrder) {}
    public record HistoryRow(String historyId, String targetTable, String targetKey, String changeType,
        String itemName, String before, String after, String reason, String registeredBy, String registeredName,
        LocalDateTime registeredAt) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Step(String projectId, int stepSn, String stepCode, String stepName, String weight,
        LocalDate startDate, LocalDate endDate, LocalDate completedDate, String status, Actor registeredBy,
        OffsetDateTime registeredAt, Actor modifiedBy, OffsetDateTime modifiedAt) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Memo(String projectId, int memoSn, LocalDate writtenDate, String thisWeek, String nextWeek,
        String request, Integer actualProgress, Actor registeredBy, OffsetDateTime registeredAt,
        Actor modifiedBy, OffsetDateTime modifiedAt) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Issue(long issueId, String projectId, String projectName, String title, String importance,
        String importanceName, LocalDate dueDate, String status, String statusName, String action, boolean overdue,
        Actor registeredBy, OffsetDateTime registeredAt, Actor modifiedBy, OffsetDateTime modifiedAt) {}
    public record Reason(String kind, String light, String value, String threshold, String formula) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MissingReason(String code, String message, String month, String personId, String assignmentId, String metric) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Completeness(boolean costComplete, boolean revenueComplete, boolean estimated,
        List<MissingReason> issues, int unknownProjectCount, String rangeStartMonth, String rangeEndMonth, String missingBeforeMonth) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Status(String projectId, String projectName, String contractType, String sourceStatus,
        String partId, String partName, boolean hq, List<Actor> pm, String light, List<Reason> reasons,
        boolean costUnknown, String planProgress, String actualProgress, String costUsage, String scheduleGap,
        String costProgressGap, Step currentStep, boolean allStepsCompleted, boolean stepsInitialized,
        String planBasis, int openIssueCount, int overdueIssueCount, LocalDate lastUpdatedDate,
        int elapsedDays, boolean updateWarning, LocalDate baseDate, String costMonth, Completeness completeness) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Data(String projectId, int year, String progressRevision, boolean readOnly, List<String> readOnlyReasons,
        Status status, List<Step> steps, List<Memo> memos, List<Issue> issues, String initializationMessage, String calculationNotice) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MutationResult(String projectId, boolean changed, String message, String progressRevision,
        Data progress, Memo memo, Issue issue) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record HistoryEntry(String historyId, String targetTable, String targetKey, String changeType,
        String itemName, String before, String after, String reason, Actor registeredBy, OffsetDateTime registeredAt) {}
    public record HistoryPage(String projectId, List<HistoryEntry> items, int total, int page, int size) {}
    public record EndDate(int stepSn, LocalDate endDate) {}
    public record Write(int year, String expectedRevision) {}
    public record Dates(int year, String expectedRevision, List<EndDate> steps) {}
    public record MemoInput(int year, String expectedRevision, String thisWeek, String nextWeek, String request,
        Integer actualProgress, boolean actualProvided) {}
    public record HistoryQuery(String targetTable, String targetKey, int page, int size) {}
    public record Preview(String projectId, String progressRevision, List<Step> steps) {}
    public record StepWrite(StepRow row, String actor, LocalDateTime now) {}
    public record MemoWrite(MemoRow row, String actor, LocalDateTime now) {}
    public record Cost(BigDecimal amount, Completeness completeness) {}
}
