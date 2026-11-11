package com.sicost.me;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import com.sicost.common.api.ApiResponse;
import com.sicost.common.validation.InputValidator;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.sicost.me.PreferenceDtos.*;

@RestController
public class PreferenceController {
    private final PreferenceService service;
    public PreferenceController(PreferenceService service) {this.service=service;}
    @GetMapping("/api/me/preferences")
    public ApiResponse<PreferencesData> getPreferences(@RequestParam Map<String,String> query) {
        query(query,Set.of());return ApiResponse.ok(service.getPreferences());
    }
    @PutMapping("/api/me/preferences/{type}/{key}")
    public ApiResponse<PreferenceSaveResult> putPreference(@PathVariable String type,@PathVariable String key,
                                                          @RequestBody JsonNode body,@RequestParam Map<String,String> query) {
        query(query,Set.of());PreferenceValidator.fields(body,Set.of("value","expectedRevision"));
        if(!body.has("value"))throw InputValidator.invalid("value");
        if(!body.has("expectedRevision"))throw InputValidator.invalid("expectedRevision");
        JsonNode token=body.get("expectedRevision");
        if(!token.isNull()&&(!token.isTextual()||token.textValue().isBlank()))throw InputValidator.invalid("expectedRevision");
        return ApiResponse.ok(service.putPreference(type,key,new PutPreferenceRequest(body.get("value"),token.isNull()?null:token.textValue())));
    }
    @DeleteMapping("/api/me/preferences/{type}/{key}")
    public ResponseEntity<Void> deletePreference(@PathVariable String type,@PathVariable String key,@RequestParam Map<String,String> query) {
        query(query,Set.of("expectedRevision"));String token=query.get("expectedRevision");
        if(token==null||token.isBlank())throw InputValidator.invalid("expectedRevision");
        service.deletePreference(type,key,token);return ResponseEntity.noContent().build();
    }
    private void query(Map<String,String> query,Set<String> allowed) {
        query.keySet().forEach(f->{if(!allowed.contains(f))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",f,null);});
    }
}
