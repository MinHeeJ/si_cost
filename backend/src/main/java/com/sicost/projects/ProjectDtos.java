package com.sicost.projects;

import com.sicost.distribution.ProjectDistributionDtos.Badge;
import com.sicost.plans.ProjectPlanDtos.ProjectPlan;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ProjectDtos {
    private ProjectDtos() {}
    public record Project(String id, String name, String customerId, String contractType, String status,
        LocalDate startDate, LocalDate endDate, String workplace, String feature, String estimateAmount,
        String originalContractAmount, String contractAmount, String budgetAmount, String teamExpenseAmount,
        String projectExpenseAmount, String budgetManual, LocalDate lossDate, String lossReason, String lossMemo, String opportunityId) {}
    public record ListRow(String id, String name, String contractType, LocalDate startDate, LocalDate endDate,
        String contractAmount, String status, String customerName, int personCount, String partName) {}
    public record ProjectListItem(String id, String name, String contractType, LocalDate startDate, LocalDate endDate,
        String contractAmount, String status, String customerName, int personCount, String partName, Badge distribution) {}
    public record ProjectsPage(List<ProjectListItem> items, long total, long unassignedCount, int page, int size, int year) {}
    public record ProjectPart(String effectiveMonth, String partId, String partName, String reason, String actor, boolean scheduled) {}
    public record ProjectPm(String userId, String name, int scopeSn) {}
    public record ProjectAssignment(String assignmentId, String developerId, String managementNo, String name,
        String jobCode, String jobName, LocalDate startDate, LocalDate endDate, String contractMm, String monthlyMmTotal, String status) {}
    public record ProjectHistory(int historySn, LocalDate date, String itemCode, String itemName, String before, String after,
        String actor, String registeredAt) {}
    public record ProjectDetail(Project project, String revision, String effectiveStatus, boolean readOnly,
        List<ProjectPart> parts, List<ProjectPm> pm, List<ProjectPlan> plans, List<ProjectAssignment> assignments,
        List<ProjectHistory> history, String actualMmTotal, String directExpenseTotal) {}
    public record ProjectDefaults(String contractAmount, String contractType, String budgetAmount, String targetMarginRate,
        String teamExpenseAmount, String projectExpenseAmount, String basisMonth, List<String> warnings) {}
    public record SaveResult(Project project, String revision, boolean changed, List<String> warnings, ProjectDefaults defaults) {}
    public record Filter(Integer year, String contractType, String status, String partId, String q, int page, int size,
        String sort, String direction) {}
    public record Query(Filter filter, LocalDate today, String month, LocalDate firstDate, LocalDate lastDate, long offset) {}
    public record Fields(String name, String customerId, LocalDate startDate, LocalDate endDate, String workplace,
        String feature, BigDecimal estimateAmount, BigDecimal contractAmount, BigDecimal budgetAmount,
        BigDecimal teamExpenseAmount, BigDecimal projectExpenseAmount, String budgetMode) {}
    public record CreateProjectRequest(int year, String contractType, String initialStatus, String partId, Fields fields) {}
    public record PutProjectRequest(int year, String expectedRevision, Fields fields) {}
    public record DefaultsRow(BigDecimal cashBase, BigDecimal marginRate, BigDecimal teamExpense, BigDecimal projectExpense) {}
    public record Reference(String id, String name, boolean active) {}
    public record Mutation(Project project, String actor, LocalDateTime now) {}
    public record HistoryWrite(String id, int sn, ProjectChange change, String actor, LocalDateTime now) {}
    public record ProjectChange(String code, String name, String before, String after) {}
    public record PartMove(String id, String oldMonth, String newMonth, String actor, LocalDateTime now) {}
    public record PeriodCheck(String id, LocalDate startDate, LocalDate endDate, String firstMonth) {}
    public record PeriodUse(String target, String month) {}
}
