package com.sicost.expenses;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ProjectExpenseDtos {
    private ProjectExpenseDtos() {}
    public record ProjectState(String contractType, String status) {}
    public record Stored(String month, int sn, String itemCode, String itemName, BigDecimal amount,
                         String memo, LocalDateTime registeredAt) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ProjectExpense(String expenseMonth, int expenseSn, String itemCode, String itemName,
                                 String amount, String memo, String registeredAt, String revision) {}
    public record ProjectExpensesData(List<ProjectExpense> items, String totalAmount, String revision) {}
    public record ExpenseResult(ProjectExpensesData value, String revision, boolean changed) {}
    public record CreateProjectExpenseRequest(Integer year, String expectedRevision, String expenseMonth,
                                              String itemCode, String amount, String memo) {}
    public record DeleteProjectExpenseRequest(Integer year, String expectedRevision) {}
    public record ExpenseKey(String id, String month, int sn) {}
    public record ExpenseWrite(ExpenseKey key, String itemCode, BigDecimal amount, String memo,
                               String actor, LocalDateTime now) {}
}
