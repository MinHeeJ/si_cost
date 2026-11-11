package com.sicost.codemappings;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.api.ApiResponse;
import com.sicost.common.validation.InputValidator;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
public class CodeMappingsController {
    private final CodeMappingsService service;
    public CodeMappingsController(CodeMappingsService service) { this.service=service; }
    @GetMapping("/api/code-mappings")
    public ApiResponse<CodeMappingsService.Page> getCodeMappings(@RequestParam MultiValueMap<String,String> query) {
        validateQuery(query,Set.of("sourceText","groupId","page","size"));
        return ApiResponse.ok(service.getCodeMappings(query.getFirst("sourceText"),query.getFirst("groupId"),
                integer(query,"page",1),integer(query,"size",50)));
    }
    @PostMapping("/api/code-mappings")
    public ResponseEntity<ApiResponse<Void>> createCodeMapping(@RequestBody JsonNode body, @RequestParam MultiValueMap<String,String> query) {
        validateQuery(query,Set.of());
        service.createCodeMapping(CodeMappingRequests.create(body));
        return ResponseEntity.status(201).body(ApiResponse.ok());
    }
    @PostMapping("/api/code-mappings/delete")
    public ResponseEntity<Void> deleteCodeMappings(@RequestBody JsonNode body, @RequestParam MultiValueMap<String,String> query) {
        validateQuery(query,Set.of());
        service.deleteCodeMappings(CodeMappingRequests.delete(body));
        return ResponseEntity.noContent().build();
    }
    private void validateQuery(MultiValueMap<String,String> query,Set<String> allowed) {
        query.forEach((field,values) -> {
            if(!allowed.contains(field)) throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 조회 항목입니다.",field,null);
            if(values.size()!=1) throw InputValidator.invalid(field);
        });
    }
    private int integer(MultiValueMap<String,String> query,String field,int fallback) {
        String value=query.getFirst(field);
        if(value==null) return fallback;
        try { return Integer.parseInt(value); }
        catch(NumberFormatException e) { throw InputValidator.invalid(field); }
    }
}
