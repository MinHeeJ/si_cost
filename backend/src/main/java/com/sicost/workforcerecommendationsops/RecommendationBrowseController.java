package com.sicost.workforcerecommendationsops;

import com.sicost.common.api.ApiResponse;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.workforcerecommendationsops.RecommendationBrowseDtos.*;

@RestController
public class RecommendationBrowseController {
    private final RecommendationBrowseService service;
    public RecommendationBrowseController(RecommendationBrowseService service){this.service=service;}
    @GetMapping("/api/workforce-recommendations/history")
    public ApiResponse<HistoryData> getWorkforceRecommendationHistory(@RequestParam MultiValueMap<String,String> query){
        return ApiResponse.ok(service.history(RecommendationBrowseInput.history(query)));
    }
    @GetMapping("/api/workforce-recommendations/targets")
    public ApiResponse<TargetsData> getWorkforceRecommendationTargets(@RequestParam MultiValueMap<String,String> query){
        RecommendationBrowseInput.query(query,Set.of());return ApiResponse.ok(service.targets());
    }
}
