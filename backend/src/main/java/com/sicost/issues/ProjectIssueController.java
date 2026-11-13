package com.sicost.issues;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.issues.ProjectIssueDtos.*;

@RestController
public class ProjectIssueController {
    private final ProjectIssueService service;
    public ProjectIssueController(ProjectIssueService service) {this.service=service;}
    @GetMapping("/api/projects/{id}/issues")
    public ApiResponse<Page> getProjectIssues(@PathVariable String id,@RequestParam MultiValueMap<String,String> query,
        @RequestBody(required=false) String body) {
        if (body!=null && !body.isBlank()) throw com.sicost.common.validation.ProjectInputPolicy.invalid("body");
        return ApiResponse.ok(service.getProjectIssues(ProjectIssueInput.id(id),ProjectIssueInput.filter(query)));
    }
    @PostMapping("/api/projects/{id}/issues")
    public ApiResponse<Result> createProjectIssue(@PathVariable String id,@RequestParam MultiValueMap<String,String> query,@RequestBody JsonNode body) {
        ProjectIssueInput.query(query,Set.of());
        return ApiResponse.ok(service.createProjectIssue(ProjectIssueInput.id(id),ProjectIssueInput.body(body)));
    }
    @PutMapping("/api/projects/{id}/issues/{issueId}")
    public ApiResponse<Result> putProjectIssue(@PathVariable String id,@PathVariable String issueId,
        @RequestParam MultiValueMap<String,String> query,@RequestBody JsonNode body) {
        ProjectIssueInput.query(query,Set.of());
        return ApiResponse.ok(service.putProjectIssue(ProjectIssueInput.id(id),ProjectIssueInput.issueId(issueId),ProjectIssueInput.body(body)));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> invalidJson(HttpMessageNotReadableException ignored,HttpServletRequest request) {
        return new ApiExceptionHandler().application(com.sicost.common.validation.ProjectInputPolicy.invalid("body"),request);
    }
}
