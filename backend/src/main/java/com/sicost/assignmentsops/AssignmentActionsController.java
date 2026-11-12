package com.sicost.assignmentsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import com.sicost.common.assignments.AssignmentMonthlyService.Month;
import com.sicost.common.validation.ProjectInputPolicy;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.*;
import static com.sicost.assignmentsops.AssignmentActionsDtos.*;
import static com.sicost.assignmentsops.AssignmentActionsInput.*;

@RestController
public class AssignmentActionsController {
    private final AssignmentActionsService service;
    public AssignmentActionsController(AssignmentActionsService service) {this.service=service;}
    @GetMapping("/api/assignments/substitution-candidates")
    public ApiResponse<Page<Candidate>> getAssignmentSubstitutionCandidates(@RequestParam Map<String,String> q) {
        query(q,Set.of("projectId","developerId","page","size"));
        return ApiResponse.ok(service.candidates(ProjectInputPolicy.text(q.get("projectId"),20,"projectId"),ProjectInputPolicy.text(q.get("developerId"),20,"developerId"),page(q),size(q)));
    }
    @GetMapping("/api/assignments/{id}/months")
    public ApiResponse<Page<Month>> getAssignmentMonths(@PathVariable String id,@RequestParam Map<String,String> q) {
        query(q,Set.of("year","page","size"));if(!q.containsKey("year"))throw ProjectInputPolicy.invalid("year");
        return ApiResponse.ok(service.getMonths(ProjectInputPolicy.text(id,20,"id"),integer(q.get("year"),"year",0),page(q),size(q)));
    }
    @PostMapping("/api/assignments/{id}/changes")
    public ApiResponse<Save> createAssignmentChange(@PathVariable String id,@RequestBody JsonNode body,@RequestParam Map<String,String> q) {
        query(q,Set.of());return ApiResponse.ok(service.change(ProjectInputPolicy.text(id,20,"id"),change(body)));
    }
    @PostMapping("/api/assignments/preview")
    public ApiResponse<Preview> createAssignmentPreview(@RequestBody JsonNode body,@RequestParam Map<String,String> q) {
        query(q,Set.of());return ApiResponse.ok(service.preview(preview(body)));
    }
    @PostMapping("/api/assignments/selection")
    public ApiResponse<Selection> createAssignmentSelection(@RequestBody JsonNode body,@RequestParam Map<String,String> q) {
        query(q,Set.of());return ApiResponse.ok(service.selection(selection(body)));
    }
    @PostMapping("/api/assignments/bulk")
    public ApiResponse<BulkResult> saveAssignmentBulk(@RequestBody JsonNode body,@RequestParam Map<String,String> q) {
        query(q,Set.of());return ApiResponse.ok(service.bulk(bulk(body)));
    }
    @PostMapping("/api/assignments/substitution-preview")
    public ApiResponse<SubPreview> createAssignmentSubstitutionPreview(@RequestBody JsonNode body,@RequestParam Map<String,String> q) {
        query(q,Set.of());return ApiResponse.ok(service.substitutionPreview(substitutionPreview(body)));
    }
    @PostMapping("/api/assignments/substitutions")
    public ApiResponse<SubResult> createAssignmentSubstitution(@RequestBody JsonNode body,@RequestParam Map<String,String> q) {
        query(q,Set.of());return ApiResponse.ok(service.substitute(substitute(body)));
    }
}
