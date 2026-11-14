package com.sicost.tentativeassignmentsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.assignmentcandidatesops.AssignmentCandidateInput;
import com.sicost.common.api.ApiException;
import com.sicost.common.api.ApiResponse;
import java.util.Map;

import org.springframework.web.bind.annotation.*;
import static com.sicost.tentativeassignmentsops.TentativeAssignmentDtos.*;

@RestController
public class TentativeAssignmentController {
    private final TentativeAssignmentService service;
    public TentativeAssignmentController(TentativeAssignmentService service) { this.service=service; }
    @PostMapping("/api/tentative-assignments/{id}/release")
    public ApiResponse<Result> saveTentativeAssignmentRelease(@PathVariable String id, @RequestBody JsonNode body,
                                                             @RequestParam Map<String,String> query) {
        if (!query.isEmpty()) throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 조회 항목입니다.",query.keySet().iterator().next(),null);
        return ApiResponse.ok(service.release(AssignmentCandidateInput.id(id),TentativeAssignmentInput.release(body)));
    }
}
