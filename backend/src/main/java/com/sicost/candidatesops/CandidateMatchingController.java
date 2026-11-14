package com.sicost.candidatesops;

import com.sicost.common.api.ApiResponse;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.candidatesops.CandidateMatchingDtos.*;

@RestController
public class CandidateMatchingController {
    private final CandidateMatchingService service;
    public CandidateMatchingController(CandidateMatchingService service) { this.service=service; }
    @GetMapping("/api/candidates/shortages")
    public ResponseEntity<ApiResponse<ShortagePage>> getCandidateShortages(@RequestParam MultiValueMap<String,String> query) {
        return response(service.shortages(CandidateMatchingInput.shortages(query)));
    }
    @GetMapping("/api/candidates/{id}/positions")
    public ResponseEntity<ApiResponse<PositionsResult>> getCandidatePositions(@PathVariable String id,@RequestParam MultiValueMap<String,String> query) {
        CandidateMatchingInput.query(query,Set.of());
        return response(service.positions(CandidateMatchingInput.id(id)));
    }
    private <T> ResponseEntity<ApiResponse<T>> response(CandidateMatchingService.Read<T> read) {
        Map<String,Object> meta=read.issues().isEmpty()?Map.of():Map.of("issues",read.issues());
        return ResponseEntity.ok().header("Cache-Control","no-store").body(new ApiResponse<>(true,meta,read.data()));
    }
}
