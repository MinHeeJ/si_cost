package com.sicost.developersops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import com.sicost.common.validation.PeopleInputPolicy;
import java.time.Clock;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.developersops.DeveloperActionsDtos.*;

@RestController
public class DeveloperActionsController {
    private final DeveloperActionsQueryService queries;
    private final DeveloperBulkStatusService bulk;
    private final DeveloperStatusRecalculationService states;
    private final Clock clock;
    public DeveloperActionsController(DeveloperActionsQueryService queries,DeveloperBulkStatusService bulk,DeveloperStatusRecalculationService states,Clock clock) {
        this.queries=queries;this.bulk=bulk;this.states=states;this.clock=clock;
    }
    @GetMapping("/api/developers/selection")
    public ApiResponse<Selection> getDeveloperSelection(@RequestParam MultiValueMap<String,String> query) {
        Filters filters=DeveloperActionsInput.filters(query,PeopleInputPolicy.today(clock));
        queries.validateFilters(filters);states.refresh();return ApiResponse.ok(queries.selection(filters));
    }
    @GetMapping("/api/developers/{id}/assignments")
    public ApiResponse<AssignmentsPage> getDeveloperAssignments(@PathVariable String id,@RequestParam MultiValueMap<String,String> query) {
        return ApiResponse.ok(queries.assignments(DeveloperActionsInput.assignments(id,query)));
    }
    @PostMapping("/api/developers/bulk-status")
    public ApiResponse<BulkResult> saveDeveloperBulkStatus(@RequestBody JsonNode body,@RequestParam MultiValueMap<String,String> query) {
        DeveloperActionsInput.query(query,Set.of());return ApiResponse.ok(bulk.save(DeveloperActionsInput.bulk(body)));
    }
}
