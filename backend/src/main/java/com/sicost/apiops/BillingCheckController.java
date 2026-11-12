package com.sicost.apiops;

import com.sicost.common.api.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import static com.sicost.apiops.BillingReviewDtos.*;

@RestController
public class BillingCheckController {
    private final BillingCheckService service;
    public BillingCheckController(BillingCheckService service) {this.service=service;}
    @GetMapping("/api/billing-checks")
    public ApiResponse<ChecksPage> getBillingChecks(@RequestParam Map<String,String> query) {
        return ApiResponse.ok(service.getBillingChecks(BillingReviewInput.checks(query)));
    }
}
