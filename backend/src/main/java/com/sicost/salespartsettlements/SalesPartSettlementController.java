package com.sicost.salespartsettlements;

import com.sicost.common.api.ApiResponse;
import com.sicost.common.validation.ProjectInputPolicy;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.salespartsettlements.SalesPartSettlementDtos.*;

@RestController
public class SalesPartSettlementController {
    private final SalesPartSettlementService service;
    public SalesPartSettlementController(SalesPartSettlementService service) {this.service=service;}
    @GetMapping("/api/sales-part-settlements")
    public ApiResponse<TabsResult> getSalesPartSettlements(@RequestParam MultiValueMap<String,String> query,
        @RequestBody(required=false) String body) {
        rejectBody(body);return ApiResponse.ok(service.getSalesPartSettlements(SalesPartSettlementInput.query(query,false)));
    }
    @GetMapping("/api/sales-part-settlements/{partId}")
    public ApiResponse<Result> getSalesPartSettlement(@PathVariable String partId,
        @RequestParam MultiValueMap<String,String> query,@RequestBody(required=false) String body) {
        rejectBody(body);return ApiResponse.ok(service.getSalesPartSettlement(partId,SalesPartSettlementInput.query(query,true)));
    }
    private static void rejectBody(String body) {if(body!=null&&!body.isBlank())throw ProjectInputPolicy.invalid("body");}
}
