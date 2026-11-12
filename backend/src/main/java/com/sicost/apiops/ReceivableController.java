package com.sicost.apiops;

import com.sicost.common.api.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import static com.sicost.apiops.BillingReviewDtos.*;

@RestController
public class ReceivableController {
    private final ReceivableService service;
    public ReceivableController(ReceivableService service) {this.service=service;}
    @GetMapping("/api/receivables")
    public ApiResponse<ReceivablesPage> getReceivables(@RequestParam Map<String,String> query) {
        return ApiResponse.ok(service.getReceivables(BillingReviewInput.receivables(query)));
    }
}
