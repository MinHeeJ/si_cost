package com.sicost.projectcostsops;

import com.sicost.apiops.ProjectAnalysisService;
import com.sicost.common.api.ApiResponse;
import com.sicost.common.validation.ProjectInputPolicy;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProjectCostExportController {
    private final ProjectAnalysisService costs;
    private final ProjectCostExportService exports;
    public ProjectCostExportController(ProjectAnalysisService costs,ProjectCostExportService exports){this.costs=costs;this.exports=exports;}
    @GetMapping("/api/project-costs/summary")
    public ApiResponse<Map<String,Object>> getProjectCostSummary(@RequestParam MultiValueMap<String,String> query,@RequestBody(required=false) String body) {
        rejectBody(body);return ApiResponse.ok(costs.getProjectCosts(ProjectCostExportInput.summary(query)).summary());
    }
    @GetMapping("/api/project-costs/export")
    public void getProjectCostExport(@RequestParam MultiValueMap<String,String> query,@RequestBody(required=false) String body,HttpServletRequest request,HttpServletResponse response)throws IOException {
        rejectBody(body);
        try(var download=exports.create(ProjectCostExportInput.export(query),request)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(download.filename(),StandardCharsets.UTF_8).build().toString());
            response.setHeader("Cache-Control","no-store");response.setContentLengthLong(download.size());download.transferTo(response.getOutputStream());
        }
    }
    private static void rejectBody(String body){if(body!=null&&!body.isBlank())throw ProjectInputPolicy.invalid("body");}
}
