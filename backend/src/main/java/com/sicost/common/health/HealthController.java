package com.sicost.common.health;

import com.sicost.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final HealthService service;
    public HealthController(HealthService service) { this.service = service; }

    @GetMapping("/api/health")
    public ApiResponse<Void> getHealth() {
        service.checkDatabase();
        return ApiResponse.ok();
    }
}
