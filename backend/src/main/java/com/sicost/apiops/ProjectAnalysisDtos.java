package com.sicost.apiops;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sicost.profitability.MonthlyCostDtos.*;
import com.sicost.common.assignments.AssignmentMonthlyService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class ProjectAnalysisDtos {
    private ProjectAnalysisDtos() {}
    public record ProjectEvidence(String projectId, BigDecimal originalAmount, BigDecimal planMm, LocalDate registeredDate) {}
    public record Expense(String projectId, String month, BigDecimal amount) {}
    public record Actor(String userId, String userName) {}
    public record ProjectManager(String projectId, String userId, String userName) {}
    public record Stage(String projectId, int stepSn, String stepCode, String stepName, BigDecimal weight,
        LocalDate startDate, LocalDate endDate, LocalDate completedDate, String registeredBy, String registeredName,
        LocalDateTime registeredAt, String modifiedBy, String modifiedName, LocalDateTime modifiedAt) {}
    public record Memo(String projectId, int memoSn, LocalDate writtenDate, BigDecimal actualProgress) {}
    public record Issue(String projectId, String importance, LocalDate dueDate, String status) {}
    public record StageTemplate(String code, String contractTypes) {}
    public record ForecastMonth(String projectId, String assignmentId, String personId, String month, BigDecimal mm) {}
    public record Snapshot(Sources sources, MonthlyCostsData costs, List<ProjectEvidence> evidence,
        List<Expense> expenses, List<Stage> stages, List<Memo> memos, List<Issue> issues,
        List<ProjectManager> managers, List<ForecastMonth> future, List<StageTemplate> templates) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MissingReason(String code, String message, String month, String personId, String assignmentId, String metric) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Completeness(boolean costComplete, boolean revenueComplete, boolean estimated,
        List<MissingReason> issues, int unknownProjectCount, String rangeStartMonth, String rangeEndMonth, String missingBeforeMonth) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CostPage(String month, LocalDate baseDate, List<Map<String,Object>> items, int total,
        int page, int size, Map<String,Object> summary) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record BoardPage(LocalDate baseDate, String currentMonth, List<Map<String,Object>> items, int total,
        int page, int size, Map<String,Object> summary, List<Map<String,Object>> staleProjects, String calculationNotice) {}
    public static Map<String,Object> fields(Object... values) {
        var result = new LinkedHashMap<String,Object>();
        for (int i=0;i<values.length;i+=2) result.put((String) values[i],values[i+1]);
        return result;
    }
}
