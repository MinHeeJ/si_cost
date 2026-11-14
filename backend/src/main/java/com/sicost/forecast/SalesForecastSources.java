package com.sicost.forecast;

import com.sicost.common.assignments.AssignmentMonthlyService.Month;
import com.sicost.common.assignments.AssignmentMonthlyService.Source;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class SalesForecastSources {
    private SalesForecastSources() {}
    public record Project(String id, String name, String type, String status, LocalDate startDate, LocalDate endDate,
                          BigDecimal amount, BigDecimal billed, String opportunityId) {}
    public record Opportunity(String id, String name, String type, String status, String startMonth,
                              Integer months, BigDecimal amount, BigDecimal probability, String projectId) {}
    public record Need(String opportunityId, int needSn, String jobCode, String startMonth, int months,
                       int count, BigDecimal monthlyMm) {}
    public record Plan(String projectId, String jobCode, BigDecimal mm) {}
    public record Person(String id, String jobCode, String useYn, String directClass, LocalDate joinDate, LocalDate retireDate) {}
    public record Leave(String developerId, LocalDate startDate, LocalDate endDate) {}
    public record Candidate(String jobCode, LocalDate availableDate) {}
    public record Standard(String code, BigDecimal value) {}
    public record Job(String code, int sortOrder) {}
    public record Assignment(Source source, List<Month> months) {}
    public record Snapshot(List<Project> projects, List<Opportunity> opportunities, List<Need> needs, List<Plan> plans,
        List<Person> people, List<Leave> leaves, List<Candidate> candidates, Map<String, BigDecimal> standards,
        List<Job> jobs, List<Assignment> assignments) {}
}
