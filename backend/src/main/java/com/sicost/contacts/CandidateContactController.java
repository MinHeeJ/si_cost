package com.sicost.contacts;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.contacts.CandidateContactDtos.*;

@RestController
public class CandidateContactController {
    private final CandidateContactService service;
    public CandidateContactController(CandidateContactService service){this.service=service;}
    @GetMapping("/api/candidates/{id}/contacts")
    public ApiResponse<Page> getCandidateContacts(@PathVariable String id,@RequestParam MultiValueMap<String,String> query) {
        return ApiResponse.ok(service.list(CandidateContactInput.id(id),CandidateContactInput.query(query)));
    }
    @PostMapping("/api/candidates/{id}/contacts")
    public ResponseEntity<ApiResponse<Created>> createCandidateContact(@PathVariable String id,
            @RequestBody JsonNode body,@RequestParam MultiValueMap<String,String> query) {
        CandidateContactInput.allowQuery(query,Set.of());
        return ResponseEntity.status(201).body(ApiResponse.ok(service.create(CandidateContactInput.id(id),CandidateContactInput.parse(body))));
    }
}
