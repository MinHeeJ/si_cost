package com.sicost.apiops;
import com.sicost.common.api.ApiResponse;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
@RestController
public class WorkforceOperationsController {
    private final WorkforceCalendarService service;
    public WorkforceOperationsController(WorkforceCalendarService service){this.service=service;}
    @GetMapping("/api/workforce-operations")
    public ApiResponse<StaffingReadDtos.CalendarPage> getWorkforceOperations(@RequestParam MultiValueMap<String,String> q,@RequestBody(required=false) String body){
        StaffingQueryInput.validate(q,Set.of("year","jobCode","gradeCode","employeeType","remainingOnly","page","size","sort","direction"),body);
        return ApiResponse.ok(service.list(q));
    }
}
