package com.sicost.assignmentsops;

import com.sicost.common.api.ApiError;
import com.sicost.common.assignments.AssignmentMonthlyService.*;
import com.sicost.common.assignments.AssignmentMonthlyService.Month;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class AssignmentActionsDtos {
    private AssignmentActionsDtos() {}
    public record Warning(String code,String message,List<String> months,List<String> assignmentIds) {}
    public record Page<T>(List<T> items,long total,int page,int size) {}
    public record Item(String id,String expectedRevision) {}
    public record Selection(List<Item> items,int total,List<String> protectedMonths) {}
    public record Filter(int year,String projectId,String jobCode,String warning,String q,boolean includeCancelled) {}
    public record SelectRequest(int year,String scope,Filter filters,List<String> ids,List<String> excludedIds) {}
    public record Bulk(int year,String type,List<Item> items,String extensionPossibility,String reasonCode,String memo) {}
    public record BulkItem(String id,String outcome,String revision,ApiError error,List<String> protectedMonths) {}
    public record BulkResult(int requested,int applied,int noChange,int rejected,List<BulkItem> items) {}
    public record Create(int year,String projectId,String developerId,String jobCode,LocalDate startDate,LocalDate endDate,
        BigDecimal contractMm,String billingUnitPrice,String inputType,String contractDeveloperId) {}
    public record Change(int year,String expectedRevision,String type,LocalDate applyDate,YearMonth applyMonth,
        LocalDate newEndDate,LocalDate withdrawalDate,BigDecimal newUnitPrice,BigDecimal newContractMm,
        String extensionPossibility,String inputType,String reasonCode,String memo) {}
    public record PreviewRequest(Create assignment,String id,Change change) {}
    public record Preview(List<Warning> warnings,List<String> affectedMonths,List<String> protectedMonths,List<Month> months) {}
    public record Candidate(String developerId,String developerName,String managementNo,String lastAssignmentId,LocalDate withdrawalDate,String defaultUnitPrice) {}
    public record CandidateSource(String developerId,String developerName,String managementNo,String lastAssignmentId,LocalDate withdrawalDate) {}
    public record CandidateQuery(String projectId,String developerId) {}
    public record Withdrawal(String assignmentId,String expectedRevision,LocalDate withdrawalDate,String reasonCode,String memo) {}
    public record Substitute(Create assignment,String mode,String previewToken,List<Withdrawal> withdrawals) {}
    public record SubPreview(String previewToken,LocalDate withdrawalDate,List<Item> candidates,List<Warning> warnings,List<String> protectedMonths,String turnkeyNotice) {}
    public record SubResult(Assignment assignment,List<String> withdrawnIds,boolean changed,List<Warning> warnings,List<String> protectedMonths,String turnkeyNotice) {}
    public record Assignment(String id,String projectId,String projectName,String developerId,String developerName,String managementNo,String jobCode,String jobName,
        LocalDate startDate,LocalDate endDate,String contractMm,String billingUnitPrice,String inputType,String contractDeveloperId,String extensionPossibility,
        int extensionCount,String earlyWithdrawal,String assignmentStatus,String yearMm,String revision,List<Warning> warnings,List<String> protectedMonths) {}
    public record Save(Assignment assignment,boolean changed,List<Warning> warnings,List<String> protectedMonths) {}
    public record Project(String id,String name,String status,String contractType,LocalDate startDate,LocalDate endDate) {}
    public record Developer(String id,String name,String managementNo,String employeeType,String directClass,String partId,String useYn,LocalDate joinDate,LocalDate retireDate) {}
    public record Labels(String projectName,String developerName,String managementNo,String jobName) {}
    public record Write(Source source,String actor,LocalDateTime now) {}
    public record Event(String id,int serial,String type,LocalDate date,String before,String after,String reason,String memo,
        BigDecimal price,BigDecimal mm,String retroactive,String closeCross,String actor,LocalDateTime now) {}
    public record PriceQuery(String developerId,String month) {}
    public record PartQuery(String projectId,String month) {}
    public record CodeQuery(String group,String code) {}
    public record TotalQuery(String developerId,String month,String excludedId) {}
    public record Overlap(String projectId,String developerId,LocalDate date) {}
    public record LeaveQuery(String developerId,LocalDate startDate,LocalDate endDate) {}
}
