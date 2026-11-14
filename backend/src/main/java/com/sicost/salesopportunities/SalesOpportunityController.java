package com.sicost.salesopportunities;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import com.sicost.salesopportunitiesops.SalesCommandInput;
import com.sicost.salesopportunitiesops.SalesCommandDtos.SaveResult;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import static com.sicost.salesopportunities.SalesOpportunityDtos.*;

@RestController
public class SalesOpportunityController {
    private final SalesOpportunityService service;
    public SalesOpportunityController(SalesOpportunityService service){this.service=service;}
    @GetMapping("/api/sales-opportunities")
    public ApiResponse<Page> getSalesOpportunities(@RequestParam Map<String,String> query){return ApiResponse.ok(service.list(SalesOpportunityInput.query(query)));}
    @GetMapping("/api/sales-opportunities/{id}")
    public ApiResponse<Opportunity> getSalesOpportunity(@PathVariable String id,@RequestParam Map<String,String> query){SalesCommandInput.noQuery(query);return ApiResponse.ok(service.get(SalesCommandInput.id(id)));}
    @PostMapping("/api/sales-opportunities")
    public ApiResponse<SaveResult> createSalesOpportunity(@RequestBody JsonNode body,@RequestParam Map<String,String> query){SalesCommandInput.noQuery(query);return ApiResponse.ok(service.create(SalesOpportunityInput.save(body,false)));}
    @PutMapping("/api/sales-opportunities/{id}")
    public ApiResponse<SaveResult> putSalesOpportunity(@PathVariable String id,@RequestBody JsonNode body,@RequestParam Map<String,String> query){SalesCommandInput.noQuery(query);return ApiResponse.ok(service.update(SalesCommandInput.id(id),SalesOpportunityInput.save(body,true)));}
}
