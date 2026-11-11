package com.sicost.apiops;

import com.sicost.common.api.ApiResponse;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.apiops.MasterHistoryDtos.*;

@RestController
public class MasterHistoryController {
    private final MasterHistoryService service;
    public MasterHistoryController(MasterHistoryService service) { this.service = service; }
    @GetMapping("/api/master-history")
    public ApiResponse<Page> getMasterHistory(@RequestParam MultiValueMap<String,String> query) {
        return ApiResponse.ok(service.list(MasterHistoryInput.parse(query)));
    }
}
