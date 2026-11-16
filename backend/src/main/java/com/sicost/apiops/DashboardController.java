package com.sicost.apiops;

import com.sicost.common.api.ApiResponse;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
public class DashboardController {
    private final DashboardService service;
    public DashboardController(DashboardService service){this.service=service;}
    @GetMapping("/api/dashboard")
    public ApiResponse<DashboardDtos.Data> getDashboard(@RequestParam MultiValueMap<String,String> query) {
        ActivityQueryInput.allow(query,Set.of("month"));
        String month=query.getFirst("month");if(month!=null)ProjectAnalysisInput.month(month);
        return ApiResponse.ok(service.getDashboard(month));
    }
}
