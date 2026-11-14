package com.sicost.assignmentcandidatesops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.api.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import static com.sicost.assignmentcandidatesops.AssignmentCandidateDtos.*;

@RestController
public class AssignmentCandidateController {
    private final AssignmentCandidateService service;
    public AssignmentCandidateController(AssignmentCandidateService service){this.service=service;}
    @PostMapping("/api/assignment-candidates/{id}/confirm")
    public ApiResponse<ConfirmationResult> createAssignmentCandidateConfirmation(@PathVariable String id,@RequestBody JsonNode body,@RequestParam Map<String,String> query) {
        noQuery(query);return ApiResponse.ok(service.confirm(AssignmentCandidateInput.id(id),AssignmentCandidateInput.confirm(id,body)));
    }
    @PostMapping("/api/assignment-candidates/{id}/drop")
    public ApiResponse<DropResult> saveAssignmentCandidateDrop(@PathVariable String id,@RequestBody JsonNode body,@RequestParam Map<String,String> query) {
        noQuery(query);return ApiResponse.ok(service.drop(AssignmentCandidateInput.id(id),AssignmentCandidateInput.drop(body)));
    }
    private void noQuery(Map<String,String> query){if(!query.isEmpty())throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 조회 항목입니다.",query.keySet().iterator().next(),null);}
}
