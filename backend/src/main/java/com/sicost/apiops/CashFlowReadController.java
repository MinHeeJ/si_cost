package com.sicost.apiops;

import com.sicost.cashflowops.CashFlowDtos.Result;
import com.sicost.cashflowops.CashFlowInput;
import com.sicost.cashflowops.CashFlowService;
import com.sicost.common.api.ApiResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
public class CashFlowReadController {
    private final CashFlowService service;
    public CashFlowReadController(CashFlowService service){this.service=service;}
    @GetMapping("/api/cash-flow")
    public ApiResponse<Result> getCashFlow(@RequestParam MultiValueMap<String,String> query) {
        return ApiResponse.ok(service.calculateCashFlow(CashFlowInput.export(query)));
    }
}
