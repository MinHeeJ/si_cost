package com.sicost.apiops;

import com.sicost.common.api.ApiResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.apiops.PeopleHistoryDtos.*;

@RestController
public class PeopleHistoryController {
    private final PeopleHistoryService service;
    public PeopleHistoryController(PeopleHistoryService service) { this.service = service; }
    @GetMapping("/api/people-history")
    public ApiResponse<Page> getPeopleHistory(@RequestParam MultiValueMap<String,String> query) {
        return ApiResponse.ok(service.list(PeopleHistoryInput.parse(query)));
    }
}
