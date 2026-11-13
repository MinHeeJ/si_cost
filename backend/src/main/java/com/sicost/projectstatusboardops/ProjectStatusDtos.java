package com.sicost.projectstatusboardops;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sicost.apiops.ProjectAnalysisDtos.Actor;
import java.time.*;
import java.util.List;

public final class ProjectStatusDtos {
    private ProjectStatusDtos() {}
    public record IssueSource(long issueId,String projectId,String projectName,String title,String importance,String importanceName,
        LocalDate dueDate,String status,String statusName,String action,String registeredId,String registeredName,LocalDateTime registeredAt,
        String modifiedId,String modifiedName,LocalDateTime modifiedAt) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Issue(long issueId,String projectId,String projectName,String title,String importance,String importanceName,
        LocalDate dueDate,String status,String statusName,String action,boolean overdue,Actor registeredBy,LocalDateTime registeredAt,
        Actor modifiedBy,LocalDateTime modifiedAt) {}
    public record IssuesPage(List<Issue> items,int total,int page,int size,int openCount,LocalDate baseDate) {}
}
