package com.sicost.apiops;

import com.sicost.common.api.ApiResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
public class MonthCloseReadController {
    private final MonthCloseReadService service;
    public MonthCloseReadController(MonthCloseReadService service){this.service=service;}
    @GetMapping("/api/month-closes")
    public ApiResponse<MonthCloseReadDtos.Result> getMonthCloses(@RequestParam MultiValueMap<String,String> query) {
        return ApiResponse.ok(service.getMonthCloses(MonthCloseReadInput.parse(query)));
    }
}
