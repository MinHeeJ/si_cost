package com.sicost.assignments;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.assignmentsops.AssignmentActionsInput;
import com.sicost.common.api.ApiResponse;
import com.sicost.common.validation.ProjectInputPolicy;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.sicost.assignmentsops.AssignmentActionsDtos.*;

@RestController
public class AssignmentController {
    private final AssignmentService service;
    public AssignmentController(AssignmentService service) {this.service=service;}
    @GetMapping("/api/assignments")
    public ApiResponse<Page<Assignment>> getAssignments(@RequestParam Map<String,String> query) {
        return ApiResponse.ok(service.list(AssignmentInput.list(query)));
    }
    @GetMapping("/api/assignments/{id}")
    public ApiResponse<Assignment> getAssignment(@PathVariable String id,@RequestParam Map<String,String> query) {
        AssignmentActionsInput.query(query,Set.of("year"));
        return ApiResponse.ok(service.get(ProjectInputPolicy.text(id,20,"id"),AssignmentInput.year(query)));
    }
    @PostMapping("/api/assignments")
    public ResponseEntity<ApiResponse<Save>> createAssignment(@RequestBody JsonNode body,@RequestParam Map<String,String> query) {
        AssignmentActionsInput.query(query,Set.of());
        return ResponseEntity.status(201).body(ApiResponse.ok(service.create(AssignmentActionsInput.create(body))));
    }
}
