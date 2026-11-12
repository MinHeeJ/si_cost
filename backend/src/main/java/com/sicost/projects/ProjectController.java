package com.sicost.projects;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import static com.sicost.projects.ProjectDtos.*;

@RestController
public class ProjectController {
    private final ProjectQueryService queries;
    private final ProjectService service;
    public ProjectController(ProjectQueryService queries,ProjectService service) { this.queries=queries;this.service=service; }
    @GetMapping("/api/projects")
    public ApiResponse<ProjectsPage> getProjects(@RequestParam Map<String,String> query) {
        return ApiResponse.ok(queries.getProjects(ProjectInput.filter(query)));
    }
    @GetMapping("/api/projects/{id}")
    public ApiResponse<ProjectDetail> getProject(@PathVariable String id,@RequestParam Map<String,String> query) {
        return ApiResponse.ok(queries.getProject(ProjectInput.id(id),ProjectInput.includeCancelled(query)));
    }
    @PostMapping("/api/projects")
    public ApiResponse<SaveResult> createProject(@RequestBody JsonNode body,@RequestParam Map<String,String> query) {
        ProjectInput.query(query,Set.of());return ApiResponse.ok(service.createProject(ProjectInput.create(body)));
    }
    @PutMapping("/api/projects/{id}")
    public ApiResponse<SaveResult> putProject(@PathVariable String id,@RequestBody JsonNode body,@RequestParam Map<String,String> query) {
        ProjectInput.query(query,Set.of());return ApiResponse.ok(service.putProject(ProjectInput.id(id),ProjectInput.update(body)));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> invalidJson(HttpMessageNotReadableException ignored,HttpServletRequest request) {
        return new ApiExceptionHandler().application(com.sicost.common.validation.ProjectInputPolicy.invalid("body"),request);
    }
}
