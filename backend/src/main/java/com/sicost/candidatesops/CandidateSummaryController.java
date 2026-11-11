package com.sicost.candidatesops;

import com.sicost.common.api.ApiException;
import com.sicost.common.api.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import static com.sicost.candidatesops.CandidateSummaryDtos.*;

@RestController
public class CandidateSummaryController {
    private final CandidateSummaryService service;
    public CandidateSummaryController(CandidateSummaryService service) { this.service = service; }
    @GetMapping("/api/candidates/summary")
    public ResponseEntity<ApiResponse<Summary>> getCandidateSummary(@RequestParam MultiValueMap<String, String> query) {
        query.keySet().forEach(field -> {
            throw new ApiException(400, "UNKNOWN_FIELD", "허용되지 않은 입력 항목입니다.", field, null);
        });
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(ApiResponse.ok(service.summary()));
    }
}
