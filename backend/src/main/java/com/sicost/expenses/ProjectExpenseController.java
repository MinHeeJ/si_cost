package com.sicost.expenses;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.*;
import com.sicost.common.validation.ProjectInputPolicy;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import static com.sicost.expenses.ProjectExpenseDtos.*;

@RestController
public class ProjectExpenseController {
    private final ProjectExpenseService service;
    public ProjectExpenseController(ProjectExpenseService service) { this.service=service; }
    @GetMapping("/api/projects/{id}/expenses")
    public ApiResponse<ProjectExpensesData> getProjectExpenses(@PathVariable String id,@RequestParam Map<String,String> query) {
        ProjectExpenseInput.query(query);
        return ApiResponse.ok(service.getProjectExpenses(ProjectExpenseInput.id(id)));
    }
    @PostMapping("/api/projects/{id}/expenses")
    public ApiResponse<ExpenseResult> createProjectExpense(@PathVariable String id,@RequestBody(required=false) JsonNode body,
                                                          @RequestParam Map<String,String> query) {
        ProjectExpenseInput.query(query);
        return ApiResponse.ok(service.createProjectExpense(ProjectExpenseInput.id(id),ProjectExpenseInput.create(body)));
    }
    @DeleteMapping("/api/projects/{id}/expenses/{expenseMonth}/{expenseSn}")
    public ApiResponse<ExpenseResult> deleteProjectExpense(@PathVariable String id,@PathVariable String expenseMonth,
        @PathVariable String expenseSn,@RequestBody(required=false) JsonNode body,@RequestParam Map<String,String> query) {
        ProjectExpenseInput.query(query);
        return ApiResponse.ok(service.deleteProjectExpense(ProjectExpenseInput.key(id,expenseMonth,expenseSn),ProjectExpenseInput.delete(body)));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> invalidJson(HttpMessageNotReadableException error,HttpServletRequest request) {
        return new ApiExceptionHandler().application(ProjectInputPolicy.invalid("body"),request);
    }
}
