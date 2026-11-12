package com.sicost.apiops;

import com.sicost.common.api.ApiResponse;
import com.sicost.common.validation.ProjectInputPolicy;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.apiops.PayrollDtos.*;

@RestController
public class PayrollController {
    private final PayrollService service;
    public PayrollController(PayrollService service) { this.service = service; }
    @GetMapping("/api/payroll")
    public ApiResponse<Page> getPayroll(@RequestParam MultiValueMap<String, String> query,
                                       @RequestBody(required = false) String body) {
        if (body != null && !body.isBlank()) throw ProjectInputPolicy.invalid("body");
        return ApiResponse.ok(service.getPayroll(PayrollInput.query(query)));
    }
}
