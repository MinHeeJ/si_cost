package com.sicost.staffingplancomparisonops;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
public class StaffingComparisonExportController {
    private final StaffingComparisonExportService service;
    public StaffingComparisonExportController(StaffingComparisonExportService service) { this.service=service; }
    @GetMapping("/api/staffing-plan-comparison/export")
    public void getStaffingPlanComparisonExport(@RequestParam MultiValueMap<String,String> query, @RequestBody(required=false) String body,
                       HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (body!=null && !body.isBlank()) throw StaffingComparisonInput.invalid("body");
        try (var file=service.export(StaffingComparisonInput.parse(query),request)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(file.filename(),StandardCharsets.UTF_8).build().toString());
            response.setHeader("Cache-Control","no-store");
            response.setContentLengthLong(file.size());
            file.transferTo(response.getOutputStream());
        }
    }
}
