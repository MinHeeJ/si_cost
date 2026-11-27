package com.sicost.apiops;

import com.sicost.common.api.ApiResponse;
import com.sicost.forecast.SalesForecastDtos.Workforce;
import com.sicost.workforceforecastsops.WorkforceForecastInput;
import java.time.Clock;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
public class WorkforceForecastController {
    private final WorkforceForecastService service;
    private final Clock clock;
    public WorkforceForecastController(WorkforceForecastService service,Clock clock){this.service=service;this.clock=clock;}
    @GetMapping("/api/workforce-forecasts")
    public ApiResponse<Workforce> getWorkforceForecasts(@RequestParam MultiValueMap<String,String> query,@RequestBody(required=false) String body){
        StaffingQueryInput.validate(query,Set.of("from","months","includeUnfilled","includeOpportunities","minimumWinProbability"),body);
        return ApiResponse.ok(service.forecast(WorkforceForecastInput.parse(query,body,clock)));
    }
}
