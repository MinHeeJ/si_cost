package com.sicost.apiops;

import com.sicost.common.api.ApiResponse;
import com.sicost.common.validation.ProjectInputPolicy;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.sicost.apiops.ProjectAnalysisDtos.*;

@RestController
public class ProjectAnalysisController {
    private final ProjectAnalysisService service;
    public ProjectAnalysisController(ProjectAnalysisService service){this.service=service;}
    @GetMapping("/api/project-costs")
    public ApiResponse<CostPage> getProjectCosts(@RequestParam MultiValueMap<String,String> query,@RequestBody(required=false) String body) {
        rejectBody(body);return ApiResponse.ok(service.getProjectCosts(ProjectAnalysisInput.query(query,false)));
    }
    @GetMapping("/api/project-status-board")
    public ApiResponse<BoardPage> getProjectStatusBoard(@RequestParam MultiValueMap<String,String> query,@RequestBody(required=false) String body) {
        rejectBody(body);return ApiResponse.ok(service.getProjectStatusBoard(ProjectAnalysisInput.query(query,true)));
    }
    private static void rejectBody(String body){if(body!=null&&!body.isBlank())throw ProjectInputPolicy.invalid("body");}
}
