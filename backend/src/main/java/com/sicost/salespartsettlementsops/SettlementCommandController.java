package com.sicost.salespartsettlementsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import com.sicost.common.validation.ProjectInputPolicy;
import com.sicost.salespartsettlements.SalesPartSettlementInput;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales-part-settlements/{partId}")
public class SettlementCommandController {
    private final SettlementCommandService commands;
    private final SettlementTrendService trends;
    public SettlementCommandController(SettlementCommandService commands,SettlementTrendService trends) {this.commands=commands;this.trends=trends;}
    @GetMapping("/trend")
    public ApiResponse<SettlementTrendService.Trend> getSalesPartSettlementTrend(@PathVariable String partId,@RequestParam MultiValueMap<String,String> params,@RequestBody(required=false) String body) {
        if(body!=null&&!body.isBlank())throw ProjectInputPolicy.invalid("body");
        params.keySet().forEach(k->{if(!java.util.Set.of("month","page","size").contains(k))throw ProjectInputPolicy.invalid(k);});
        return ApiResponse.ok(trends.get(partId,SalesPartSettlementInput.query(params,true)));
    }
    @PutMapping("/card")
    public ApiResponse<SettlementCommandService.Mutation> saveSalesPartSettlementCard(@PathVariable String partId,@RequestParam MultiValueMap<String,String> params,@RequestBody JsonNode body) {
        rejectQuery(params);return ApiResponse.ok(commands.saveCard(partId,body));
    }
    @PostMapping("/confirm")
    public ApiResponse<SettlementCommandService.Mutation> createSalesPartSettlementConfirmation(@PathVariable String partId,@RequestParam MultiValueMap<String,String> params,@RequestBody JsonNode body) {
        rejectQuery(params);return ApiResponse.ok(commands.confirm(partId,body));
    }
    @PostMapping("/cancel")
    public ApiResponse<SettlementCommandService.Mutation> createSalesPartSettlementCancellation(@PathVariable String partId,@RequestParam MultiValueMap<String,String> params,@RequestBody JsonNode body) {
        rejectQuery(params);return ApiResponse.ok(commands.cancel(partId,body));
    }
    private static void rejectQuery(MultiValueMap<String,String> params) {if(!params.isEmpty())throw ProjectInputPolicy.invalid(params.keySet().iterator().next());}
}
