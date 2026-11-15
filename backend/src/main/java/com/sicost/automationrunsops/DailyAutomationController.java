package com.sicost.automationrunsops;

import com.sicost.common.api.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.sicost.automationrunsops.DailyAutomationDtos.*;

@RestController
public class DailyAutomationController {
    private final DailyAutomationService service;
    public DailyAutomationController(DailyAutomationService service) { this.service=service; }
    @PostMapping("/api/automation-runs/{jobCode}/run-today")
    public ResponseEntity<ApiResponse<CommandResult>> createAutomationRunToday(@PathVariable String jobCode,HttpServletRequest request) throws java.io.IOException {
        service.requireManualEnabled();
        if(!request.getParameterMap().isEmpty())throw new ApiException(400,"UNKNOWN_FIELD","오늘 실행에는 조회 조건을 지정할 수 없습니다.");
        if(request.getInputStream().read()!=-1)throw new ApiException(400,"UNKNOWN_FIELD","오늘 실행에는 본문을 지정할 수 없습니다.","body",null);
        CommandResult result=service.runToday(jobCode);
        return ResponseEntity.status(result.completed()?200:202).body(ApiResponse.ok(result));
    }
}
