package com.sicost.rolesops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
public class RoleStopController {
    private final RoleStopService service;
    public RoleStopController(RoleStopService service) {this.service=service;}
    @PostMapping("/api/roles/{id}/stop")
    public ApiResponse<RoleStopDtos.Result> saveRoleStop(@PathVariable String id,@RequestBody JsonNode body,
        @RequestParam Map<String,String> query,HttpServletRequest request) {
        return ApiResponse.ok(service.stop(id,RoleStopInput.parse(body,query),request));
    }
}
