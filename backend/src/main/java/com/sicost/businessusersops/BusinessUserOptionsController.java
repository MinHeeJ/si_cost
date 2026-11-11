package com.sicost.businessusersops;

import com.sicost.common.api.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import static com.sicost.businessusersops.BusinessUserOptionsDtos.*;

@RestController
public class BusinessUserOptionsController {
    private final BusinessUserOptionsService service;
    public BusinessUserOptionsController(BusinessUserOptionsService service) { this.service = service; }
    @GetMapping("/api/business-users/options")
    public ResponseEntity<ApiResponse<Options>> getBusinessUserOptions(@RequestParam MultiValueMap<String, String> query) {
        String roleId = BusinessUserOptionsInput.role(query);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.ok(service.options(roleId)));
    }
}
