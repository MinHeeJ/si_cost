package com.sicost.pm;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.*;
import com.sicost.common.validation.ProjectInputPolicy;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import static com.sicost.pm.ProjectPmDtos.*;

@RestController
public class ProjectPmController {
    private final ProjectPmService service;
    public ProjectPmController(ProjectPmService service) { this.service=service; }
    @GetMapping("/api/projects/{id}/pm")
    public ApiResponse<ProjectPmData> getProjectPm(@PathVariable String id,@RequestParam Map<String,String> query,
                                                  @RequestBody(required=false) JsonNode body) {
        ProjectPmInput.query(query);
        if (body!=null) throw ProjectInputPolicy.invalid(body.isObject() && body.fieldNames().hasNext()?body.fieldNames().next():"body");
        return ApiResponse.ok(service.getProjectPm(ProjectPmInput.id(id)));
    }
    @PutMapping("/api/projects/{id}/pm")
    public ApiResponse<SaveProjectPmResult> saveProjectPm(@PathVariable String id,@RequestBody(required=false) JsonNode body,
                                                        @RequestParam Map<String,String> query) {
        ProjectPmInput.query(query);return ApiResponse.ok(service.saveProjectPm(ProjectPmInput.id(id),ProjectPmInput.save(body)));
    }
    @DeleteMapping("/api/projects/{id}/pm")
    public ApiResponse<SaveProjectPmResult> deleteProjectPm(@PathVariable String id,@RequestBody(required=false) JsonNode body,
                                                          @RequestParam Map<String,String> query) {
        ProjectPmInput.query(query);return ApiResponse.ok(service.deleteProjectPm(ProjectPmInput.id(id),ProjectPmInput.delete(body)));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> invalidJson(HttpMessageNotReadableException error,HttpServletRequest request) {
        return new ApiExceptionHandler().application(ProjectInputPolicy.invalid("body"),request);
    }
}
