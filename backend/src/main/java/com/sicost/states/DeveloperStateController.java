package com.sicost.states;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.sicost.states.DeveloperStateDtos.*;

@RestController
public class DeveloperStateController {
    private final DeveloperStateService service;
    public DeveloperStateController(DeveloperStateService service){this.service=service;}
    @GetMapping("/api/developers/{id}/states")
    public ApiResponse<States> getDeveloperStates(@PathVariable String id,@RequestParam Map<String,String> query){
        DeveloperStateInput.query(query);return ApiResponse.ok(service.get(id));
    }
    @PostMapping("/api/developers/{id}/states")
    public ResponseEntity<ApiResponse<Created>> createDeveloperState(@PathVariable String id,@RequestBody JsonNode body,@RequestParam Map<String,String> query){
        DeveloperStateInput.query(query);return ResponseEntity.status(201).body(ApiResponse.ok(service.create(id,DeveloperStateInput.create(body))));
    }
    @PatchMapping("/api/developers/{id}/states/{sn}/end")
    public ApiResponse<Saved> putDeveloperStateEnd(@PathVariable String id,@PathVariable int sn,@RequestBody JsonNode body,@RequestParam Map<String,String> query){
        DeveloperStateInput.query(query);return ApiResponse.ok(service.putEnd(id,sn,DeveloperStateInput.end(body)));
    }
    @PostMapping("/api/developers/{id}/states/{sn}/cancel")
    public ApiResponse<Saved> saveDeveloperStateCancel(@PathVariable String id,@PathVariable int sn,@RequestBody JsonNode body,@RequestParam Map<String,String> query){
        DeveloperStateInput.query(query);return ApiResponse.ok(service.cancel(id,sn,DeveloperStateInput.cancel(body)));
    }
}
