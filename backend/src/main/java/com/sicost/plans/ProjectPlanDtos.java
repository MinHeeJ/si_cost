package com.sicost.plans;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ProjectPlanDtos {
    private ProjectPlanDtos() {}
    public record ProjectPlan(String jobCode, String jobName, String useYn, String planMm,
                              String actualMm, String differenceMm, String source) {}
    public record ProjectPlansData(List<ProjectPlan> items, String planMmTotal, String actualMmTotal,
                                   String differenceMmTotal, String revision) {}
    public record PlanInput(String jobCode, String planMm) {}
    public record SaveProjectPlansRequest(Integer year, String expectedRevision, List<PlanInput> items) {}
    public record SaveProjectPlansResult(ProjectPlansData value, String revision, boolean changed) {}
    public record StoredPlan(String jobCode, String jobName, String useYn, BigDecimal planMm,
                             BigDecimal actualMm, String source) {}
    public record PlanMutation(String id, String jobCode, BigDecimal planMm, String actor, LocalDateTime now) {}
    public record PlanChange(String jobCode, BigDecimal before, BigDecimal after) {}
}
