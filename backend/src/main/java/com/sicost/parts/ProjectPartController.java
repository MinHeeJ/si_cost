package com.sicost.parts;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.*;
import com.sicost.common.validation.ProjectInputPolicy;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import static com.sicost.parts.ProjectPartDtos.*;

@RestController
public class ProjectPartController {
    private final ProjectPartService service;
    public ProjectPartController(ProjectPartService service) { this.service=service; }
    @GetMapping("/api/projects/{id}/parts")
    public ApiResponse<ProjectPartsData> getProjectParts(@PathVariable String id,@RequestParam Map<String,String> query) {
        ProjectPartInput.query(query);return ApiResponse.ok(service.getProjectParts(ProjectPartInput.id(id)));
    }
    @PutMapping("/api/projects/{id}/parts")
    public ApiResponse<SaveProjectPartResult> saveProjectPart(@PathVariable String id,@RequestBody(required=false) JsonNode body,
                                                            @RequestParam Map<String,String> query) {
        ProjectPartInput.query(query);return ApiResponse.ok(service.saveProjectPart(ProjectPartInput.id(id),ProjectPartInput.save(body)));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> invalidJson(HttpMessageNotReadableException error,HttpServletRequest request) {
        return new ApiExceptionHandler().application(ProjectInputPolicy.invalid("body"),request);
    }
}
