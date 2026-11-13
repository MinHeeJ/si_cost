package com.sicost.profitability;

import com.sicost.common.api.ApiResponse;
import com.sicost.common.validation.ProjectInputPolicy;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.profitability.MonthlyCostDtos.*;

@RestController
public class MonthlyCostController {
    private final MonthlyCostService service;
    public MonthlyCostController(MonthlyCostService service) { this.service = service; }
    @GetMapping("/api/monthly-costs")
    public ApiResponse<MonthlyCostsData> getMonthlyCosts(@RequestParam MultiValueMap<String, String> query,
        @RequestBody(required = false) String body) {
        if (body != null && !body.isBlank()) throw ProjectInputPolicy.invalid("body");
        return ApiResponse.ok(service.getMonthlyCosts(MonthlyCostInput.query(query)));
    }
}
