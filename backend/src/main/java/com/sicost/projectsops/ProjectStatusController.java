package com.sicost.projectsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiResponse;
import com.sicost.common.validation.ProjectInputPolicy;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.*;
import static com.sicost.projectsops.ProjectStatusDtos.*;

@RestController
public class ProjectStatusController {
    private final ProjectStatusService service;
    public ProjectStatusController(ProjectStatusService service) {this.service=service;}
    @GetMapping("/api/projects/defaults")
    public ApiResponse<Defaults> getProjectDefaults(@RequestParam Map<String,String> query) {
        ProjectStatusInput.query(query,Set.of("contractAmount","contractType"));
        return ApiResponse.ok(service.defaults(ProjectInputPolicy.amount(query.get("contractAmount"),"contractAmount"),
            ProjectStatusInput.choice(query.get("contractType"),Set.of("TK","MO","DP","IN"),"contractType")));
    }
    @GetMapping("/api/projects/options")
    public ApiResponse<Options> getProjectOptions(@RequestParam Map<String,String> query) {
        ProjectStatusInput.query(query,Set.of("purpose","q"));
        return ApiResponse.ok(service.options(ProjectStatusInput.choice(query.get("purpose"),Set.of("ASSIGNMENT","BILLING"),"purpose"),
            ProjectInputPolicy.optionalText(query.get("q"),100,"q")));
    }
    @GetMapping("/api/projects/{id}/loss-preview")
    public ApiResponse<Preview> getProjectLossPreview(@PathVariable String id,@RequestParam Map<String,String> query) {
        ProjectStatusInput.query(query,Set.of());return ApiResponse.ok(service.preview(ProjectStatusInput.id(id)));
    }
    @PutMapping("/api/projects/{id}/progress")
    public ApiResponse<Result> saveProjectProgress(@PathVariable String id,@RequestBody JsonNode body,@RequestParam Map<String,String> query) {
        ProjectStatusInput.query(query,Set.of());return ApiResponse.ok(service.progress(ProjectStatusInput.id(id),ProjectStatusInput.progress(body)));
    }
    @PutMapping("/api/projects/{id}/loss")
    public ApiResponse<Result> saveProjectLoss(@PathVariable String id,@RequestBody JsonNode body,@RequestParam Map<String,String> query) {
        ProjectStatusInput.query(query,Set.of());return ApiResponse.ok(service.loss(ProjectStatusInput.id(id),ProjectStatusInput.loss(body)));
    }
}
