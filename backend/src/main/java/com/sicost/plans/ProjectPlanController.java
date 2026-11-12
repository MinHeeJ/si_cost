package com.sicost.plans;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiError;
import com.sicost.common.api.ApiExceptionHandler;
import com.sicost.common.api.ApiResponse;
import com.sicost.common.validation.ProjectInputPolicy;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import static com.sicost.plans.ProjectPlanDtos.*;

@RestController
public class ProjectPlanController {
    private final ProjectPlanService service;
    public ProjectPlanController(ProjectPlanService service) { this.service = service; }
    @GetMapping("/api/projects/{id}/plans")
    public ApiResponse<ProjectPlansData> getProjectPlans(@PathVariable String id, @RequestParam Map<String, String> query) {
        ProjectPlanInput.query(query);
        return ApiResponse.ok(service.getProjectPlans(ProjectPlanInput.id(id)));
    }
    @PutMapping("/api/projects/{id}/plans")
    public ApiResponse<SaveProjectPlansResult> saveProjectPlans(@PathVariable String id, @RequestBody(required = false) JsonNode body,
                                                               @RequestParam Map<String, String> query) {
        ProjectPlanInput.query(query);
        return ApiResponse.ok(service.saveProjectPlans(ProjectPlanInput.id(id), ProjectPlanInput.save(body)));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> invalidJson(HttpMessageNotReadableException error, HttpServletRequest request) {
        return new ApiExceptionHandler().application(ProjectInputPolicy.invalid("body"), request);
    }
}
