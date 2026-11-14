package com.sicost.businessplansops;

import com.sicost.common.api.ApiException;
import com.sicost.common.api.ApiResponse;
import java.time.YearMonth;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.businessplansops.BusinessPlanPerformanceDtos.*;

@RestController
public class BusinessPlanPerformanceController {
    private final BusinessPlanPerformanceService service;
    public BusinessPlanPerformanceController(BusinessPlanPerformanceService service) {this.service=service;}
    @GetMapping("/api/business-plans/performance")
    public ApiResponse<Data> getBusinessPlanPerformance(@RequestParam MultiValueMap<String,String> values) {
        values.forEach((key,items)->{
            if(!Set.of("year","month").contains(key))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",key,null);
            if(items.size()!=1)throw invalid(key);
        });
        String year=values.getFirst("year"),month=values.getFirst("month");
        if(year==null||!year.matches("[1-9][0-9]{0,3}"))throw invalid("year");
        if(month==null||!month.matches("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])"))throw invalid("month");
        int y=Integer.parseInt(year);if(YearMonth.parse(month).getYear()!=y)throw invalid("month");
        return ApiResponse.ok(service.getBusinessPlanPerformance(new Query(y,month)));
    }
    private ApiException invalid(String field) {return new ApiException(400,"VALIDATION_ERROR","연도·기준월의 형식과 일치를 확인해 주세요.",field,null);}
}
