package com.sicost.dashboardops;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
public class DashboardExportController {
    private final DashboardExportService exports;
    public DashboardExportController(DashboardExportService exports) { this.exports=exports; }
    @GetMapping("/api/dashboard/export")
    public void getDashboardExport(@RequestParam MultiValueMap<String,String> query,@RequestBody(required=false) String body,
                                   HttpServletRequest request,HttpServletResponse response) throws IOException {
        String month=DashboardExportInput.month(query,body);
        try(var download=exports.create(month,request,response)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(download.filename(),StandardCharsets.UTF_8).build().toString());
            response.setHeader("Cache-Control","no-store");
            response.setContentLengthLong(download.size());
            download.transferTo(response.getOutputStream());
        }
    }
}
