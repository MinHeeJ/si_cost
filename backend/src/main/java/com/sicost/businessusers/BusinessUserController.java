package com.sicost.businessusers;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.sicost.businessusers.BusinessUserDtos.*;

@RestController
public class BusinessUserController {
    private final BusinessUserService service;
    public BusinessUserController(BusinessUserService service) {this.service=service;}
    @GetMapping("/api/business-users")
    public ApiResponse<Page> getBusinessUsers(@RequestParam Map<String,String> query) {
        return ApiResponse.ok(service.list(BusinessUserInput.filter(query)));
    }
    @GetMapping("/api/business-users/{id}")
    public ApiResponse<User> getBusinessUser(@PathVariable String id,@RequestParam Map<String,String> query) {
        BusinessUserInput.query(query,Set.of());return ApiResponse.ok(service.get(id));
    }
    @PostMapping("/api/business-users")
    public ResponseEntity<ApiResponse<Result>> createBusinessUser(@RequestBody JsonNode body,@RequestParam Map<String,String> query,HttpServletRequest request) {
        BusinessUserInput.query(query,Set.of());
        return ResponseEntity.status(201).body(ApiResponse.ok(service.create(BusinessUserInput.create(body),request)));
    }
    @PutMapping("/api/business-users/{id}")
    public ApiResponse<Result> putBusinessUser(@PathVariable String id,@RequestBody JsonNode body,@RequestParam Map<String,String> query) {
        BusinessUserInput.query(query,Set.of());return ApiResponse.ok(service.update(id,BusinessUserInput.edit(body)));
    }
}
