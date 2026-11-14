package com.sicost.costanalysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.*;
import com.sicost.common.validation.ProjectInputPolicy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.costanalysis.ProjectCostDtos.*;

@RestController
@RequestMapping("/api/projects/{id}")
public class ProjectCostController {
    private final ProjectCostService service;
    public ProjectCostController(ProjectCostService service) {this.service=service;}
    @GetMapping("/cost-analysis")
    public ApiResponse<Detail> getProjectCostAnalysis(@PathVariable String id,@RequestParam MultiValueMap<String,String> query,@RequestBody(required=false) String body) {
        rejectBody(body);return ApiResponse.ok(service.getProjectCostAnalysis(ProjectCostInput.id(id),ProjectCostInput.query(query,false)));
    }
    @GetMapping("/cost-analysis/months")
    public ApiResponse<Months> getProjectCostMonths(@PathVariable String id,@RequestParam MultiValueMap<String,String> query,@RequestBody(required=false) String body) {
        rejectBody(body);return ApiResponse.ok(service.getProjectCostMonths(ProjectCostInput.id(id),ProjectCostInput.query(query,false)));
    }
    @GetMapping("/cost-analysis/assignments")
    public ApiResponse<Assignments> getProjectCostAssignments(@PathVariable String id,@RequestParam MultiValueMap<String,String> query,@RequestBody(required=false) String body) {
        rejectBody(body);return ApiResponse.ok(service.getProjectCostAssignments(ProjectCostInput.id(id),ProjectCostInput.query(query,false)));
    }
    @GetMapping("/cost-analysis/causes")
    public ApiResponse<Causes> getProjectCostCauses(@PathVariable String id,@RequestParam MultiValueMap<String,String> query,@RequestBody(required=false) String body) {
        rejectBody(body);return ApiResponse.ok(service.getProjectCostCauses(ProjectCostInput.id(id),ProjectCostInput.query(query,false)));
    }
    @PostMapping("/cost-analysis/simulation")
    public ApiResponse<SimulationResult> createProjectCostSimulation(@PathVariable String id,@RequestParam MultiValueMap<String,String> query,@RequestBody JsonNode body) {
        ProjectCostInput.query(query,true);return ApiResponse.ok(service.createProjectCostSimulation(ProjectCostInput.id(id),ProjectCostInput.simulation(body)));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> invalidJson(HttpMessageNotReadableException ignored,HttpServletRequest request) {
        return new ApiExceptionHandler().application(ProjectInputPolicy.invalid("body"),request);
    }
    private static void rejectBody(String body) {if(body!=null&&!body.isBlank())throw ProjectInputPolicy.invalid("body");}
}
