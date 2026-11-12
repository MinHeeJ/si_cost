package com.sicost.projectsops;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class ProjectStatusDtos {
    private ProjectStatusDtos() {}
    public record Project(String id,String name,String customerId,String contractType,String status,
        LocalDate startDate,LocalDate endDate,String workplace,String feature,String estimateAmount,
        String originalContractAmount,String contractAmount,String budgetAmount,String teamExpenseAmount,
        String projectExpenseAmount,String budgetManual,LocalDate lossDate,String lossReason,String lossMemo,String opportunityId) {}
    public record Option(String id,String name,String contractType,LocalDate startDate,LocalDate endDate,
        String contractAmount,String status,String customerName,long personCount,String partName,Object distribution) {}
    public record Options(List<Option> items) {}
    public record Defaults(String contractAmount,String contractType,String budgetAmount,String targetMarginRate,
        String teamExpenseAmount,String projectExpenseAmount,String basisMonth,List<String> warnings) {}
    public record Assignment(String assignmentId,String developerId,String managementNo,String name,String jobCode,
        String jobName,LocalDate startDate,LocalDate endDate,String contractMm,String monthlyMmTotal,String status) {}
    public record Preview(LocalDate basisDate,long currentPersonCount,long futurePersonCount,int currentAssignmentCount,
        int futureAssignmentCount,List<Assignment> currentAssignments,List<Assignment> futureAssignments,String previewToken,String revision) {}
    public record Progress(int year,String expectedRevision) {}
    public record Loss(int year,String expectedRevision,String lossReason,String lossMemo,String previewToken) {}
    public record Result(Project value,String revision,boolean changed) {}
    public record Selection(String purpose,String q,LocalDate today,String month) {}
    public record ProjectWrite(String id,String status,LocalDate date,String reason,String memo,String actor,LocalDateTime now) {}
    public record Event(String id,int serial,String before,String after,LocalDate date,String actor,LocalDateTime now) {}
    public record AssignmentRow(String id,String developerId,LocalDate startDate,LocalDate endDate,BigDecimal contractMm,BigDecimal price,String status) {}
    public record AssignmentWrite(String id,String projectId,LocalDate date,boolean future,String actor,LocalDateTime now) {}
    public record AssignmentEvent(String id,int serial,String type,LocalDate date,String before,String after,String memo,String closeCross,String actor,LocalDateTime now) {}
    public record ValueHistory(int serial,String type,LocalDate date,BigDecimal mm,BigDecimal price,String corrected,Integer target) {}
    public record MonthRow(String month,String fixed) {}
    public record MonthWrite(String id,String projectId,String developerId,String month,int days,BigDecimal mm,BigDecimal price,String actor,LocalDateTime now) {}
    public record CostWrite(String id,String month,String actor,LocalDateTime now) {}
    public record PreviewSnapshot(String id,LocalDate date,String assignments) {}
}
