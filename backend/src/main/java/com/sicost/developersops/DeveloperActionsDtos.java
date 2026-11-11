package com.sicost.developersops;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.*;
import java.util.*;

public final class DeveloperActionsDtos {
    private DeveloperActionsDtos() {}
    public record Filters(String employeeType, String jobCode, String directClass, String workStatus,
                          String q, boolean includeStopped, String sort, String direction, LocalDate today) {}
    public record SelectionItem(String id, String revision) {}
    public record Selection(List<SelectionItem> items) {}
    public record BulkRequest(String targetUseYn, List<SelectionItem> items) {}
    public record Id(String id) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Rejected(String id, String code, String message, Object details) {}
    public record BulkResult(List<Id> applied, List<Id> noChange, List<Rejected> rejected,
                             int appliedCount, int noChangeCount, int rejectedCount) {}
    public record Assignment(String id, String projectId, String projectName, String jobCode, String jobName,
                             LocalDate startDate, LocalDate endDate, String contractMm, String assignmentStatus) {}
    public record AssignmentsPage(List<Assignment> items, long total, int page, int size) {}
    public record AssignmentQuery(String id, int page, int size, long offset) {}
    public record Project(String id, String name, LocalDate startDate, LocalDate endDate) {}
    public record Mutation(String id, String useYn, String status, String actor, LocalDateTime now) {}
    public record Base(String id, String managementNo, String name, String employeeType, String companyId,
                       String gradeCode, String jobCode, String subJobCode, String positionCode, String directClass,
                       String partId, LocalDate hireDate, LocalDate retireDate, String status, String homeAreaCode,
                       String homeAddress, String phone, String useYn, String regUserId, LocalDateTime regDttm,
                       String modUserId, LocalDateTime modDttm) {}
    public record Snapshot(Base developer, List<Map<String,Object>> prices, List<Map<String,Object>> skills,
                           List<Map<String,Object>> states) {}
}
