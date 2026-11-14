package com.sicost.apiops;

import com.sicost.common.api.ApiResponse;
import com.sicost.common.validation.ProjectInputPolicy;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
public class RateRenegotiationController {
    private final RateRenegotiationService service;
    public RateRenegotiationController(RateRenegotiationService service) { this.service = service; }
    @GetMapping("/api/rate-renegotiations")
    public ApiResponse<RateRenegotiationDtos.Result> getRateRenegotiations(@RequestParam MultiValueMap<String, String> query,
        @RequestBody(required = false) String body) {
        if (body != null && !body.isBlank()) throw ProjectInputPolicy.invalid("body");
        return ApiResponse.ok(service.getRateRenegotiations(RateRenegotiationInput.query(query)));
    }
}
