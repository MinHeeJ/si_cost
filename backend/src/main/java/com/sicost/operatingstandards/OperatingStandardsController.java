package com.sicost.operatingstandards;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import com.sicost.common.api.ApiException;
import java.util.Map;
import java.util.Set;
import com.sicost.common.validation.InputValidator;
import com.sicost.common.validation.PeriodValidator;
import org.springframework.web.bind.annotation.*;
import static com.sicost.operatingstandards.OperatingStandardsDtos.*;

@RestController
public class OperatingStandardsController {
    private final OperatingStandardsService service;
    public OperatingStandardsController(OperatingStandardsService service) {this.service=service;}
    @GetMapping("/api/operating-standards")
    public ApiResponse<Items> getOperatingStandards(@RequestParam Map<String,String> query) {
        query(query,Set.of("month")); String month=query.get("month");
        if(month!=null)PeriodValidator.month(month);
        return ApiResponse.ok(service.list(month));
    }
    @GetMapping("/api/operating-standards/{key}/history")
    public ApiResponse<History> getOperatingStandardHistory(@PathVariable String key,@RequestParam Map<String,String> query) {
        query(query,Set.of());
        InputValidator.text(key,30,"key");return ApiResponse.ok(service.history(key));
    }
    @PutMapping("/api/operating-standards")
    public ApiResponse<Result> saveOperatingStandards(@RequestBody JsonNode body,@RequestParam Map<String,String> query) {
        query(query,Set.of());
        return ApiResponse.ok(service.save(OperatingStandardsInput.parse(body)));
    }
    private void query(Map<String,String> query,Set<String> allowed) {
        query.keySet().forEach(key->{if(!allowed.contains(key))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",key,null);});
    }
}
