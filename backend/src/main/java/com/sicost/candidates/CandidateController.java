package com.sicost.candidates;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class CandidateController {
    private final CandidateService service;
    private final CandidateQueryService queries;
    public CandidateController(CandidateService service,CandidateQueryService queries){this.service=service;this.queries=queries;}
    @GetMapping("/api/candidates")
    public ApiResponse<JsonNode> getCandidates(@RequestParam Map<String,String> query){return ApiResponse.ok(queries.list(CandidateInput.query(query)));}
    @GetMapping("/api/candidates/{id}")
    public ApiResponse<JsonNode> getCandidate(@PathVariable String id,@RequestParam Map<String,String> query){CandidateInput.allowQuery(query,Set.of());return ApiResponse.ok(queries.get(CandidateInput.id(id)));}
    @PostMapping("/api/candidates")
    public ResponseEntity<ApiResponse<JsonNode>> createCandidate(@RequestBody JsonNode body,@RequestParam Map<String,String> query){CandidateInput.allowQuery(query,Set.of());return ResponseEntity.status(201).body(ApiResponse.ok(service.create(CandidateInput.parse(body,false))));}
    @PutMapping("/api/candidates/{id}")
    public ApiResponse<JsonNode> putCandidate(@PathVariable String id,@RequestBody JsonNode body,@RequestParam Map<String,String> query){CandidateInput.allowQuery(query,Set.of());return ApiResponse.ok(service.update(CandidateInput.id(id),CandidateInput.parse(body,true)));}
}
