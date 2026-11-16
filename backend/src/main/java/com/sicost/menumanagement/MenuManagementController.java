package com.sicost.menumanagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import static com.sicost.menumanagement.MenuManagementDtos.*;

@RestController
public class MenuManagementController {
    private final MenuManagementService service;
    public MenuManagementController(MenuManagementService service) { this.service=service; }
    @GetMapping("/api/menu-management")
    public ApiResponse<ManagedMenuTree> getMenuManagement(@RequestParam Map<String,String> query, @RequestBody(required=false) JsonNode body) {
        MenuManagementInput.query(query);
        if (body != null) throw new com.sicost.common.api.ApiException(400,"UNKNOWN_FIELD","조회 요청에는 본문을 지정할 수 없습니다.","body",null);
        return ApiResponse.ok(service.getMenuManagement());
    }
    @PutMapping("/api/menu-management")
    public ApiResponse<SaveResult> putMenuManagement(@RequestParam Map<String,String> query, @RequestBody JsonNode body) {
        MenuManagementInput.query(query);
        return ApiResponse.ok(service.putMenuManagement(MenuManagementInput.tree(body)));
    }
}
