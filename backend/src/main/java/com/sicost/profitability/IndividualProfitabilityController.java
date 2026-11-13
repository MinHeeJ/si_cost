package com.sicost.profitability;

import com.sicost.common.api.ApiResponse;
import com.sicost.common.validation.ProjectInputPolicy;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.profitability.MonthlyCostDtos.*;

@RestController
public class IndividualProfitabilityController {
    private final IndividualProfitabilityService service;
    public IndividualProfitabilityController(IndividualProfitabilityService service) { this.service = service; }
    @GetMapping("/api/individual-profitability")
    public ApiResponse<IndividualProfitabilityData> getIndividualProfitability(@RequestParam MultiValueMap<String, String> query,
        @RequestBody(required = false) String body) {
        rejectBody(body);
        return ApiResponse.ok(service.getIndividualProfitability(IndividualProfitabilityInput.query(query, false)));
    }
    @GetMapping("/api/individual-profitability/{personId}")
    public ApiResponse<IndividualProfitabilityPersonData> getIndividualProfitabilityPerson(@PathVariable String personId,
        @RequestParam MultiValueMap<String, String> query, @RequestBody(required = false) String body) {
        rejectBody(body);
        return ApiResponse.ok(service.getIndividualProfitabilityPerson(personId, IndividualProfitabilityInput.query(query, true)));
    }
    private static void rejectBody(String body) {
        if (body != null && !body.isBlank()) throw ProjectInputPolicy.invalid("body");
    }
}
