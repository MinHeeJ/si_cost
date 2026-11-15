package com.sicost.workforcerecommendations;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.workforcerecommendations.WorkforceRecommendationDtos.*;

@RestController
public class WorkforceRecommendationController {
    private final WorkforceRecommendationService service;
    public WorkforceRecommendationController(WorkforceRecommendationService service){this.service=service;}
    @PostMapping("/api/workforce-recommendations")
    public ApiResponse<Data> createWorkforceRecommendation(@RequestBody JsonNode body,@RequestParam MultiValueMap<String,String> query){WorkforceRecommendationInput.noQuery(query);return ApiResponse.ok(service.create(WorkforceRecommendationInput.create(body)));}
    @GetMapping("/api/workforce-recommendations/{rcmdId}")
    public ApiResponse<Data> getWorkforceRecommendation(@PathVariable String rcmdId,@RequestParam MultiValueMap<String,String> query){WorkforceRecommendationInput.noQuery(query);return ApiResponse.ok(service.get(WorkforceRecommendationInput.id(rcmdId)));}
}
