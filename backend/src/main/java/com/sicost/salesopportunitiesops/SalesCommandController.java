package com.sicost.salesopportunitiesops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import static com.sicost.salesopportunitiesops.SalesCommandDtos.*;

@RestController
public class SalesCommandController {
    private final SalesCommandService service;
    private final SalesWinService wins;
    public SalesCommandController(SalesCommandService service,SalesWinService wins){this.service=service;this.wins=wins;}
    @PostMapping("/api/sales-opportunities/quote-preview")
    public ApiResponse<Quote> createSalesOpportunityQuotePreview(@RequestBody JsonNode body,@RequestParam Map<String,String> query){SalesCommandInput.noQuery(query);return ApiResponse.ok(service.preview(SalesCommandInput.preview(body)));}
    @PostMapping("/api/sales-opportunities/{id}/stage")
    public ApiResponse<SaveResult> saveSalesOpportunityStage(@PathVariable String id,@RequestBody JsonNode body,@RequestParam Map<String,String> query){SalesCommandInput.noQuery(query);return ApiResponse.ok(service.stage(SalesCommandInput.id(id),SalesCommandInput.stage(body)));}
    @PostMapping("/api/sales-opportunities/{id}/approval")
    public ApiResponse<SaveResult> createSalesOpportunityApproval(@PathVariable String id,@RequestBody JsonNode body,@RequestParam Map<String,String> query){SalesCommandInput.noQuery(query);return ApiResponse.ok(service.approval(SalesCommandInput.id(id),SalesCommandInput.approval(body)));}
    @PostMapping("/api/sales-opportunities/{id}/lose")
    public ApiResponse<SaveResult> createSalesOpportunityLoss(@PathVariable String id,@RequestBody JsonNode body,@RequestParam Map<String,String> query){SalesCommandInput.noQuery(query);return ApiResponse.ok(service.lose(SalesCommandInput.id(id),SalesCommandInput.loss(body)));}
    @PostMapping("/api/sales-opportunities/{id}/stop")
    public ApiResponse<SaveResult> saveSalesOpportunityStop(@PathVariable String id,@RequestBody JsonNode body,@RequestParam Map<String,String> query){SalesCommandInput.noQuery(query);return ApiResponse.ok(service.stop(SalesCommandInput.id(id),SalesCommandInput.revisionRequest(body)));}
    @PostMapping("/api/sales-opportunities/{id}/resume")
    public ApiResponse<SaveResult> saveSalesOpportunityResume(@PathVariable String id,@RequestBody JsonNode body,@RequestParam Map<String,String> query){SalesCommandInput.noQuery(query);return ApiResponse.ok(service.resume(SalesCommandInput.id(id),SalesCommandInput.revisionRequest(body)));}
    @PostMapping("/api/sales-opportunities/{id}/win")
    public ApiResponse<WinResult> createSalesOpportunityWin(@PathVariable String id,@RequestBody JsonNode body,@RequestParam Map<String,String> query){SalesCommandInput.noQuery(query);return ApiResponse.ok(wins.win(SalesCommandInput.id(id),SalesCommandInput.win(body)));}
}
