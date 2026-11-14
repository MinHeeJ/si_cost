package com.sicost.apiops;
import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
@RestController
public class TentativeSelectionController {
    private final TentativeSelectionService service;
    public TentativeSelectionController(TentativeSelectionService service){this.service=service;}
    @PostMapping("/api/tentative-assignments")
    public ApiResponse<TentativeSelectionDtos.Result> createTentativeAssignment(@RequestBody JsonNode body,@RequestParam MultiValueMap<String,String> q){
        StaffingQueryInput.validate(q,Set.of(),null);return ApiResponse.ok(service.create(TentativeSelectionInput.parse(body)));
    }
}
