package com.sicost.monthclosesops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import com.sicost.common.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import static com.sicost.monthclosesops.MonthCloseDtos.*;

@RestController
@RequestMapping("/api/month-closes/{month}")
public class MonthCloseController {
    private final MonthCloseService service;
    public MonthCloseController(MonthCloseService service){this.service=service;}
    private void query(HttpServletRequest request){if(!request.getParameterMap().isEmpty())throw new ApiException(400,"UNKNOWN_FIELD","이 명령은 query 입력을 받지 않습니다.",request.getParameterMap().keySet().iterator().next(),null);}
    @PostMapping("/checks")
    public ApiResponse<ChecksSaved> saveMonthCloseChecks(@PathVariable String month,@RequestBody JsonNode body,HttpServletRequest request){query(request);return ApiResponse.ok(service.saveMonthCloseChecks(month,body));}
    @PostMapping("/preview")
    public ApiResponse<Preview> createMonthClosePreview(@PathVariable String month,@RequestBody JsonNode body,HttpServletRequest request){query(request);return ApiResponse.ok(service.createMonthClosePreview(month,body));}
    @PostMapping("/close")
    public ApiResponse<Commit> saveMonthClose(@PathVariable String month,@RequestBody JsonNode body,HttpServletRequest request){query(request);return ApiResponse.ok(service.saveMonthClose(month,body,request));}
    @PostMapping("/reopen-preview")
    public ApiResponse<ReopenPreview> createMonthCloseReopenPreview(@PathVariable String month,@RequestBody JsonNode body,HttpServletRequest request){query(request);return ApiResponse.ok(service.createMonthCloseReopenPreview(month,body));}
    @PostMapping("/reopen")
    public ApiResponse<Commit> saveMonthCloseReopen(@PathVariable String month,@RequestBody JsonNode body,HttpServletRequest request){query(request);return ApiResponse.ok(service.saveMonthCloseReopen(month,body,request));}
}
