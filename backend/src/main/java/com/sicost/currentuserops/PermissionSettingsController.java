package com.sicost.currentuserops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.api.ApiResponse;
import com.sicost.common.validation.InputValidator;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.currentuserops.PermissionSettingsDtos.*;

@RestController
public class PermissionSettingsController {
    private final PermissionSettingsService service;
    public PermissionSettingsController(PermissionSettingsService service) { this.service=service; }
    @GetMapping("/api/current-user/permission-settings")
    public ApiResponse<Settings> getCurrentUserPermissionSettings(@RequestParam MultiValueMap<String,String> query,
                                                                  @RequestBody(required=false) JsonNode body) {
        if (!query.isEmpty()) throw unknown(query.keySet().iterator().next());
        if (body!=null) {
            if (body.isObject() && body.fieldNames().hasNext()) throw unknown(body.fieldNames().next());
            throw InputValidator.invalid("body");
        }
        return ApiResponse.ok(service.getCurrentUserPermissionSettings());
    }
    private ApiException unknown(String field) {
        return new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",field,null);
    }
}
