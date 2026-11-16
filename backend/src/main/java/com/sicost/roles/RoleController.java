package com.sicost.roles;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.sicost.roles.RoleDtos.*;

@RestController
public class RoleController {
    private final RoleService service;
    public RoleController(RoleService service) {this.service=service;}
    @GetMapping("/api/roles")
    public ApiResponse<ManagedRolesData> getRoles(@RequestParam Map<String,String> query) {
        RoleInput.query(query);return ApiResponse.ok(service.list());
    }
    @GetMapping("/api/roles/{id}")
    public ApiResponse<ManagedRoleDetail> getRole(@PathVariable String id,@RequestParam Map<String,String> query) {
        RoleInput.query(query);return ApiResponse.ok(service.get(id));
    }
    @PostMapping("/api/roles")
    public ResponseEntity<ApiResponse<ManagedRoleSaveResult>> createRole(@RequestBody JsonNode body,@RequestParam Map<String,String> query,HttpServletRequest request) {
        RoleInput.query(query);return ResponseEntity.status(201).body(ApiResponse.ok(service.create(RoleInput.create(body),request)));
    }
    @PutMapping("/api/roles/{id}")
    public ApiResponse<ManagedRoleSaveResult> putRole(@PathVariable String id,@RequestBody JsonNode body,@RequestParam Map<String,String> query,HttpServletRequest request) {
        RoleInput.query(query);return ApiResponse.ok(service.update(id,RoleInput.edit(body),request));
    }
}
