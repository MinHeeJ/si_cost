package com.sicost.history;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import static com.sicost.history.AssignmentHistoryDtos.*;

@RestController
public class AssignmentHistoryController {
    private final AssignmentHistoryService service;
    public AssignmentHistoryController(AssignmentHistoryService service) {this.service=service;}
    @GetMapping("/api/assignments/{id}/history")
    public ApiResponse<Page> getAssignmentHistory(@PathVariable String id,@RequestParam Map<String,String> query) {
        Query input=AssignmentHistoryInput.query(id,query);
        return ApiResponse.ok(service.getAssignmentHistory(input,AssignmentHistoryInput.integer(query.get("year"),"year")));
    }
    @PostMapping("/api/assignments/{id}/history/{sn}/corrections")
    public ApiResponse<Result> createAssignmentHistoryCorrection(@PathVariable String id,@PathVariable int sn,
        @RequestBody JsonNode body,@RequestParam Map<String,String> query) {
        AssignmentHistoryInput.query(query,Set.of());
        return ApiResponse.ok(service.createAssignmentHistoryCorrection(AssignmentHistoryInput.id(id),AssignmentHistoryInput.serial(sn),AssignmentHistoryInput.correction(body)));
    }
}
