package com.sicost.distribution;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import com.sicost.common.validation.ProjectInputPolicy;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.distribution.ProjectDistributionDtos.*;

@RestController
public class ProjectDistributionController {
    private final ProjectDistributionService service;
    public ProjectDistributionController(ProjectDistributionService service) { this.service=service; }
    @GetMapping("/api/projects/{id}/distribution")
    public ApiResponse<Data> getProjectDistribution(@PathVariable String id, @RequestParam MultiValueMap<String,String> query,
                                                    @RequestBody(required=false) JsonNode body) {
        validate(query,Set.of("step"),body);String step=query.getFirst("step");ProjectDistributionService.requireStep(step);
        return ApiResponse.ok(service.get(id,step));
    }
    @GetMapping("/api/projects/{id}/distribution/comparison")
    public ApiResponse<Comparison> getProjectDistributionComparison(@PathVariable String id, @RequestParam MultiValueMap<String,String> query,
                                                                   @RequestBody(required=false) JsonNode body) {
        validate(query,Set.of(),body);return ApiResponse.ok(service.comparison(id));
    }
    @GetMapping("/api/projects/{id}/distribution/uploads")
    public ApiResponse<Uploads> getProjectDistributionUploads(@PathVariable String id, @RequestParam MultiValueMap<String,String> query,
                                                            @RequestBody(required=false) JsonNode body) {
        validate(query,Set.of(),body);return ApiResponse.ok(service.uploads(id));
    }
    private void validate(MultiValueMap<String,String> query, Set<String> allowed, JsonNode body) {
        query.forEach((key,values)->{ if(!allowed.contains(key) || values.size()!=1) throw ProjectInputPolicy.invalid(key); });
        if(body!=null && (!body.isObject() || !body.isEmpty())) {
            throw ProjectInputPolicy.invalid(body.isObject()?body.fieldNames().next():"body");
        }
    }
}
