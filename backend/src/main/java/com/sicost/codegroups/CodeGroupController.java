package com.sicost.codegroups;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.*;
import com.sicost.common.validation.InputValidator;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.sicost.codegroups.CodeGroupDtos.*;

@RestController
public class CodeGroupController {
    private final CodeGroupService service;
    public CodeGroupController(CodeGroupService service) { this.service = service; }
    @GetMapping("/api/code-groups")
    public ApiResponse<Groups> getCodeGroups(@RequestParam Map<String,String> query) {
        query(query,Set.of()); return ApiResponse.ok(service.groups());
    }
    @PostMapping("/api/code-groups")
    public ResponseEntity<ApiResponse<Void>> createCodeGroup(@RequestBody JsonNode body, @RequestParam Map<String,String> query) {
        query(query,Set.of()); service.create(CodeGroupInput.create(body));
        return ResponseEntity.status(201).body(ApiResponse.ok());
    }
    @GetMapping("/api/code-groups/{group}/codes")
    public ApiResponse<Page> getGroupCodes(@PathVariable String group, @RequestParam Map<String,String> query) {
        query(query,Set.of("page","size"));
        return ApiResponse.ok(service.page(InputValidator.groupId(group),number(query,"page",1),number(query,"size",50)));
    }
    @PutMapping("/api/code-groups/{group}/codes")
    public ApiResponse<SaveResult> saveGroupCodes(@PathVariable String group, @RequestBody JsonNode body, @RequestParam Map<String,String> query) {
        query(query,Set.of()); return ApiResponse.ok(service.save(InputValidator.groupId(group),CodeGroupInput.save(body)));
    }
    @DeleteMapping("/api/code-groups/{group}/codes/{code}")
    public ResponseEntity<Void> deleteGroupCode(@PathVariable String group, @PathVariable String code, @RequestParam Map<String,String> query) {
        query(query,Set.of("expectedRevision"));
        String revision = InputValidator.text(query.get("expectedRevision"),200,"expectedRevision");
        service.delete(InputValidator.groupId(group),InputValidator.code(code),revision);
        return ResponseEntity.noContent().build();
    }
    private static void query(Map<String,String> query, Set<String> allowed) {
        query.keySet().forEach(key -> { if (!allowed.contains(key)) throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",key,null); });
    }
    private static int number(Map<String,String> query, String field, int fallback) {
        if (!query.containsKey(field)) return fallback;
        try { return Integer.parseInt(query.get(field)); }
        catch (NumberFormatException e) { throw InputValidator.invalid(field); }
    }
}
