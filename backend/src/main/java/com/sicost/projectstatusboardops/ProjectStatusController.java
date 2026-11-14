package com.sicost.projectstatusboardops;

import com.sicost.common.api.ApiResponse;
import com.sicost.common.validation.ProjectInputPolicy;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController("projectStatusBoardController")
public class ProjectStatusController {
    private final ProjectStatusReadService reads;
    private final ProjectStatusExportService exports;
    public ProjectStatusController(ProjectStatusReadService reads,ProjectStatusExportService exports){this.reads=reads;this.exports=exports;}
    @GetMapping("/api/project-status-board/summary")
    public ApiResponse<Map<String,Object>> getProjectStatusBoardSummary(@RequestParam MultiValueMap<String,String> query,@RequestBody(required=false) String body){rejectBody(body);return ApiResponse.ok(reads.summary(ProjectStatusInput.summary(query)));}
    @GetMapping("/api/project-status-board/issues")
    public ApiResponse<ProjectStatusDtos.IssuesPage> getProjectStatusIssues(@RequestParam MultiValueMap<String,String> query,@RequestBody(required=false) String body){rejectBody(body);return ApiResponse.ok(reads.issues(ProjectStatusInput.issues(query)));}
    @GetMapping("/api/project-status-board/export")
    public void getProjectStatusBoardExport(@RequestParam MultiValueMap<String,String> query,@RequestBody(required=false) String body,HttpServletRequest request,HttpServletResponse response)throws IOException {
        rejectBody(body);
        try(var download=exports.create(ProjectStatusInput.export(query),request)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(download.filename(),StandardCharsets.UTF_8).build().toString());
            response.setHeader("Cache-Control","no-store");response.setContentLengthLong(download.size());download.transferTo(response.getOutputStream());
        }
    }
    private static void rejectBody(String body){if(body!=null&&!body.isBlank())throw ProjectInputPolicy.invalid("body");}
}
