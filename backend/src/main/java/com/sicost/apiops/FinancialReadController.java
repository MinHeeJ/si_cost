package com.sicost.apiops;

import com.sicost.common.api.ApiResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import static com.sicost.apiops.FinancialReadDtos.*;

@RestController
public class FinancialReadController {
    private final FinancialReadService service;
    public FinancialReadController(FinancialReadService service){this.service=service;}
    @GetMapping("/api/financial-contributions")
    public ApiResponse<Contributions> getFinancialContributions(@RequestParam MultiValueMap<String,String> query){
        FinancialReadInput.query(query,false);
        return ApiResponse.ok(service.getFinancialContributions(FinancialReadInput.period(query.getFirst("month"))));
    }
    @GetMapping("/api/financial-history")
    public ApiResponse<HistoryPage> getFinancialHistory(@RequestParam MultiValueMap<String,String> query){
        return ApiResponse.ok(service.getFinancialHistory(FinancialReadInput.history(query)));
    }
}
