package com.sicost.budget;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ProjectBudgetDtos {
    private ProjectBudgetDtos() {}
    public record ProjectBudgetData(String budgetAmount, String budgetManual, String revision,
                                    String autoPreviewAmount, String targetMarginRate, String basisMonth) {}
    public record SaveProjectBudgetRequest(Integer year, String expectedRevision, BigDecimal budgetAmount) {}
    public record SaveProjectBudgetResult(ProjectBudgetData value, String revision, boolean changed) {}
    public record Stored(String id, String contractType, String status, LocalDate endDate,
                         BigDecimal contractAmount, BigDecimal budgetAmount, String budgetManual) {}
    public record Mutation(String id, BigDecimal amount, String actor, LocalDateTime now) {}
    public record BudgetHistory(String id, int historySn, String before, String after,
                                String actor, LocalDateTime now) {}
}
