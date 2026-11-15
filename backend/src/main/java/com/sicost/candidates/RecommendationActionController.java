package com.sicost.candidates;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import static com.sicost.candidates.RecommendationActionDtos.*;

@RestController
public class RecommendationActionController {
    private final RecommendationActionService service;
    public RecommendationActionController(RecommendationActionService service){this.service=service;}
    @PostMapping("/api/workforce-recommendations/{rcmdId}/candidates/{rank}/exclude")
    public ApiResponse<Result> createWorkforceRecommendationExclusion(@PathVariable String rcmdId,@PathVariable String rank,@RequestBody JsonNode body,@RequestParam Map<String,String> query){
        RecommendationActionInput.query(query);return ApiResponse.ok(service.exclude(RecommendationActionInput.key(rcmdId,rank),RecommendationActionInput.exclusion(body)));
    }
    @PostMapping("/api/workforce-recommendations/{rcmdId}/candidates/{rank}/tentative-assignment")
    public ApiResponse<Result> createWorkforceRecommendationTentativeAssignment(@PathVariable String rcmdId,@PathVariable String rank,@RequestBody JsonNode body,@RequestParam Map<String,String> query){
        RecommendationActionInput.query(query);return ApiResponse.ok(service.assignTentatively(RecommendationActionInput.key(rcmdId,rank),RecommendationActionInput.tentative(body)));
    }
}
