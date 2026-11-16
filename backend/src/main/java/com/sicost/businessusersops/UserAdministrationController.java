package com.sicost.businessusersops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.businessusers.BusinessUserDtos.Result;
import com.sicost.businessusers.BusinessUserInput;
import com.sicost.common.api.ApiResponse;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.*;
import static com.sicost.businessusersops.UserAdministrationDtos.*;

@RestController
public class UserAdministrationController {
    private final UserAdministrationService service;
    public UserAdministrationController(UserAdministrationService service) {this.service=service;}
    @GetMapping("/api/business-users/{id}/scopes")
    public ApiResponse<Scopes> getBusinessUserScopes(@PathVariable String id,@RequestParam Map<String,String> query) {
        BusinessUserInput.query(query,Set.of());return ApiResponse.ok(service.scopes(id));
    }
    @GetMapping("/api/business-users/{id}/access-logs")
    public ApiResponse<Activities> getBusinessUserAccessLogs(@PathVariable String id,@RequestParam Map<String,String> query) {
        return ApiResponse.ok(service.activities(UserAdministrationInput.activities(id,query)));
    }
    @PostMapping("/api/business-users/status-changes")
    public ApiResponse<StatusResults> saveBusinessUserStatusChanges(@RequestBody JsonNode body,@RequestParam Map<String,String> query) {
        BusinessUserInput.query(query,Set.of());return ApiResponse.ok(service.bulk(UserAdministrationInput.bulk(body)));
    }
    @PostMapping("/api/business-users/{id}/stop")
    public ApiResponse<Result> saveBusinessUserStop(@PathVariable String id,@RequestBody JsonNode body,@RequestParam Map<String,String> query) {
        return change(id,body,query,"STOP");
    }
    @PostMapping("/api/business-users/{id}/resume")
    public ApiResponse<Result> saveBusinessUserResume(@PathVariable String id,@RequestBody JsonNode body,@RequestParam Map<String,String> query) {
        return change(id,body,query,"RESUME");
    }
    @PostMapping("/api/business-users/{id}/unlock")
    public ApiResponse<Result> saveBusinessUserUnlock(@PathVariable String id,@RequestBody JsonNode body,@RequestParam Map<String,String> query) {
        return change(id,body,query,"UNLOCK");
    }
    private ApiResponse<Result> change(String id,JsonNode body,Map<String,String> query,String action) {
        BusinessUserInput.query(query,Set.of());return ApiResponse.ok(service.change(id,UserAdministrationInput.revision(body),action));
    }
}
