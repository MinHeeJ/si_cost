package com.sicost.salesparts;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.sicost.salesparts.SalesPartDtos.*;

@RestController
public class SalesPartController {
    private final SalesPartService service;
    public SalesPartController(SalesPartService service) { this.service=service; }
    @GetMapping("/api/sales-parts")
    public ApiResponse<Page> getSalesParts(@RequestParam Map<String,String> query) {
        return ApiResponse.ok(service.list(SalesPartInput.list(query)));
    }
    @GetMapping("/api/sales-parts/{id}")
    public ApiResponse<Detail> getSalesPart(@PathVariable String id,@RequestParam Map<String,String> query) {
        SalesPartInput.query(query,Set.of());return ApiResponse.ok(service.detail(id));
    }
    @PostMapping("/api/sales-parts")
    public ResponseEntity<ApiResponse<Result>> createSalesPart(@RequestBody JsonNode body,@RequestParam Map<String,String> query) {
        SalesPartInput.query(query,Set.of());
        return ResponseEntity.status(201).body(ApiResponse.ok(service.create(SalesPartInput.create(body))));
    }
    @PatchMapping("/api/sales-parts/{id}")
    public ApiResponse<Result> putSalesPart(@PathVariable String id,@RequestBody JsonNode body,@RequestParam Map<String,String> query) {
        SalesPartInput.query(query,Set.of());return ApiResponse.ok(service.patch(id,SalesPartInput.patch(body)));
    }
}
