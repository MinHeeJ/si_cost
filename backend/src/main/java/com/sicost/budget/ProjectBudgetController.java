package com.sicost.budget;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import com.sicost.common.api.ApiError;
import com.sicost.common.api.ApiExceptionHandler;
import com.sicost.common.validation.ProjectInputPolicy;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import static com.sicost.budget.ProjectBudgetDtos.*;

@RestController
public class ProjectBudgetController {
    private final ProjectBudgetService service;
    public ProjectBudgetController(ProjectBudgetService service) { this.service=service; }
    @GetMapping("/api/projects/{id}/budget")
    public ApiResponse<ProjectBudgetData> getProjectBudget(@PathVariable String id,@RequestParam Map<String,String> query) {
        ProjectBudgetInput.query(query);
        return ApiResponse.ok(service.getProjectBudget(ProjectBudgetInput.id(id)));
    }
    @PutMapping("/api/projects/{id}/budget")
    public ApiResponse<SaveProjectBudgetResult> saveProjectBudget(@PathVariable String id,@RequestBody(required=false) JsonNode body,
                                                                @RequestParam Map<String,String> query) {
        ProjectBudgetInput.query(query);
        return ApiResponse.ok(service.saveProjectBudget(ProjectBudgetInput.id(id),ProjectBudgetInput.save(body)));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> invalidJson(HttpMessageNotReadableException error,HttpServletRequest request) {
        return new ApiExceptionHandler().application(ProjectInputPolicy.invalid("body"),request);
    }
}
