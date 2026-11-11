package com.sicost.developers;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import com.sicost.common.validation.InputValidator;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class DeveloperController {
    private final DeveloperService service;
    private final DeveloperQueryService queries;
    public DeveloperController(DeveloperService service,DeveloperQueryService queries){this.service=service;this.queries=queries;}
    @GetMapping("/api/developers")
    public ApiResponse<JsonNode> getDevelopers(@RequestParam Map<String,String> query){return ApiResponse.ok(queries.list(DeveloperInput.query(query)));}
    @GetMapping("/api/developers/{id}")
    public ApiResponse<JsonNode> getDeveloper(@PathVariable String id,@RequestParam Map<String,String> query){DeveloperInput.allowQuery(query,Set.of());return ApiResponse.ok(queries.get(id(id)));}
    @PostMapping("/api/developers")
    public ResponseEntity<ApiResponse<JsonNode>> createDeveloper(@RequestBody JsonNode body,@RequestParam Map<String,String> query){DeveloperInput.allowQuery(query,Set.of());return ResponseEntity.status(201).body(ApiResponse.ok(service.create(DeveloperInput.parse(body,false))));}
    @PutMapping("/api/developers/{id}")
    public ApiResponse<JsonNode> putDeveloper(@PathVariable String id,@RequestBody JsonNode body,@RequestParam Map<String,String> query){DeveloperInput.allowQuery(query,Set.of());return ApiResponse.ok(service.update(id(id),DeveloperInput.parse(body,true)));}
    private String id(String id){if(!id.matches("DEV-[0-9]{6,16}"))throw InputValidator.invalid("id");return id;}
}
