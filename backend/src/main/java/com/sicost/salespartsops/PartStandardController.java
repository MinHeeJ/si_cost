package com.sicost.salespartsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.sicost.salespartsops.PartStandardDtos.*;

@RestController
public class PartStandardController {
    private final PartStandardService service;
    public PartStandardController(PartStandardService service) {this.service=service;}
    @GetMapping("/api/sales-parts/defaults")
    public ApiResponse<Defaults> getSalesPartDefaults(@RequestParam Map<String,String> query) {
        PartStandardInput.query(query,Set.of("effectiveMonth"));
        return ApiResponse.ok(service.defaults(query.get("effectiveMonth")));
    }
    @GetMapping("/api/sales-parts/options")
    public ApiResponse<Options> getSalesPartOptions(@RequestParam Map<String,String> query) {
        PartStandardInput.query(query,Set.of());return ApiResponse.ok(service.options());
    }
    @PostMapping("/api/sales-parts/{id}/standards")
    public ResponseEntity<ApiResponse<Result>> createSalesPartStandard(@PathVariable String id,@RequestBody JsonNode body,
                                                                      @RequestParam Map<String,String> query) {
        PartStandardInput.query(query,Set.of());Result result=service.save(id,body);
        return ResponseEntity.status(result.changed()?201:200).body(ApiResponse.ok(result));
    }
}
