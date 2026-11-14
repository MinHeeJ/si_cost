package com.sicost.apiops;
import com.sicost.common.api.ApiResponse;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
@RestController
public class AssignmentCandidateQueryController {
    private final AssignmentCandidateQueryService service;
    public AssignmentCandidateQueryController(AssignmentCandidateQueryService service){this.service=service;}
    @GetMapping("/api/assignment-candidates")
    public ApiResponse<StaffingReadDtos.CandidatePage> getAssignmentCandidates(@RequestParam MultiValueMap<String,String> q,@RequestBody(required=false) String body){
        StaffingQueryInput.validate(q,Set.of("page","size","sort","direction"),body);
        return ApiResponse.ok(service.list(StaffingQueryInput.paging(q,Set.of("id","projectId","developerId"),"id")));
    }
}
