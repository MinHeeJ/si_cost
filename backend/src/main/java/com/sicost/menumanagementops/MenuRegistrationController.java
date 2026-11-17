package com.sicost.menumanagementops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.api.ApiResponse;
import com.sicost.menumanagement.MenuManagementDtos.SaveResult;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.sicost.menumanagementops.MenuRegistrationDtos.*;

@RestController
public class MenuRegistrationController {
    private final MenuRegistrationService service;
    public MenuRegistrationController(MenuRegistrationService service) { this.service = service; }
    @GetMapping("/api/menu-management/screens")
    public ApiResponse<AvailableScreens> getMenuManagementScreens(@RequestParam Map<String,String> query,
                                                                @RequestBody(required=false) JsonNode body) {
        MenuRegistrationInput.query(query);
        if (body != null) throw new ApiException(400,"UNKNOWN_FIELD","조회 요청에는 본문을 지정할 수 없습니다.","body",null);
        return ApiResponse.ok(service.getMenuManagementScreens());
    }
    @PostMapping("/api/menu-management/menus")
    public ResponseEntity<ApiResponse<SaveResult>> createMenu(@RequestParam Map<String,String> query, @RequestBody JsonNode body) {
        MenuRegistrationInput.query(query);
        return ResponseEntity.status(201).body(ApiResponse.ok(service.createMenu(MenuRegistrationInput.create(body))));
    }
}
