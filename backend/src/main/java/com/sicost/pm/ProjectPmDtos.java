package com.sicost.pm;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ProjectPmDtos {
    private ProjectPmDtos() {}
    public record ProjectPm(String userId,String name,int scopeSn) {}
    public record ProjectPmData(List<ProjectPm> items,String revision) {}
    public record SaveProjectPmRequest(String userId,boolean replaceConfirmed,String expectedRevision) {}
    public record DeleteProjectPmRequest(String userId,String expectedRevision) {}
    public record SaveProjectPmResult(ProjectPmData value,String revision,boolean changed) {}
    public record ProjectSource(String id,String status,LocalDate endDate) {}
    public record Scope(String userId,int scopeSn,String projectId,String name) {}
    public record ScopeWrite(String userId,int scopeSn,String projectId,String actor,LocalDateTime now) {}
    public record PmHistory(String projectId,int historySn,String before,String after,String actor,LocalDateTime now) {}
}
