package com.sicost.pm;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ProjectPmDtos {
    private ProjectPmDtos() {}
    public record ProjectPm(String userId,String name,int scopeSn,String status) {
        public ProjectPm(String id,String name,int sn) {this(id,name,sn,null);}
    }
    public record ProjectPmData(List<ProjectPm> items,String revision) {}
    public record SaveProjectPmRequest(String userId,boolean replaceConfirmed,String expectedRevision) {}
    public record DeleteProjectPmRequest(String userId,String expectedRevision) {}
    public record SaveProjectPmResult(ProjectPmData value,String revision,boolean changed) {}
    public record ProjectSource(String id,String status,LocalDate endDate) {}
    public record Scope(String userId,int scopeSn,String projectId,String name,String status) {
        @org.apache.ibatis.annotations.AutomapConstructor
        public Scope {}
        public Scope(String id,int sn,String project,String name) {this(id,sn,project,name,null);}
    }
    public record ScopeWrite(String userId,int scopeSn,String projectId,String actor,LocalDateTime now) {}
    public record PmHistory(String projectId,int historySn,String before,String after,String actor,LocalDateTime now) {}
}
