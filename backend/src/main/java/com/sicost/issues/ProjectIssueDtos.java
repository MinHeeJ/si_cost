package com.sicost.issues;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

public final class ProjectIssueDtos {
    private ProjectIssueDtos() {}
    public record Input(int year, String expectedRevision, String title, LocalDate dueDate,
                        String importance, String status, String action) {}
    public record Filter(String status, String importance, int page, int size) {}
    public record Project(String projectId, String projectName, String status, LocalDate endDate) {}
    public record Row(long issueId, String projectId, String projectName, String title,
        String importance, String importanceName, LocalDate dueDate, String status, String statusName,
        String action, String regUserId, String regUserName, LocalDateTime regDttm,
        String modUserId, String modUserName, LocalDateTime modDttm) {}
    public record Actor(String userId, String userName) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Issue(long issueId, String projectId, String projectName, String title,
        String importance, String importanceName, LocalDate dueDate, String status, String statusName,
        String action, boolean overdue, Actor registeredBy, OffsetDateTime registeredAt,
        Actor modifiedBy, OffsetDateTime modifiedAt) {}
    public record Page(List<Issue> items, int total, int page, int size, int openCount, LocalDate baseDate) {}
    public record Mutation(String projectId, Long issueId, Input input, String actor, LocalDateTime now) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Result(String projectId, boolean changed, String message, String progressRevision,
                         Object progress, Object memo, Issue issue) {}
}
