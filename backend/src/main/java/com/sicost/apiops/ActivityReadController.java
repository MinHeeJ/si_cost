package com.sicost.apiops;

import com.sicost.common.api.ApiResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.apiops.ActivityReadDtos.*;

@RestController
public class ActivityReadController {
    private final ActivityReadService service;
    public ActivityReadController(ActivityReadService service){this.service=service;}
    @GetMapping("/api/notifications")
    public ApiResponse<Notifications> getNotifications(@RequestParam MultiValueMap<String,String> query) {
        return ApiResponse.ok(service.getNotifications(ActivityQueryInput.parse(query,false)));
    }
    @GetMapping("/api/automation-runs")
    public ApiResponse<AutomationRuns> getAutomationRuns(@RequestParam MultiValueMap<String,String> query) {
        return ApiResponse.ok(service.getAutomationRuns(ActivityQueryInput.parse(query,true)));
    }
}
