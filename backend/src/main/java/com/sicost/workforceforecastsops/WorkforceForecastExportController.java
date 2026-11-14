package com.sicost.workforceforecastsops;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.http.ContentDisposition;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
public class WorkforceForecastExportController {
    private final WorkforceForecastExportService exports;
    private final Clock clock;
    public WorkforceForecastExportController(WorkforceForecastExportService exports, Clock clock) {
        this.exports = exports; this.clock = clock;
    }
    @GetMapping("/api/workforce-forecasts/export")
    public void getWorkforceForecastExport(@RequestParam MultiValueMap<String,String> query, @RequestBody(required = false) String body,
                                          HttpServletRequest request, HttpServletResponse response) throws IOException {
        var input = WorkforceForecastInput.parse(query, body, clock);
        try (var download = exports.export(input, request)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", ContentDisposition.attachment().filename(download.filename(), StandardCharsets.UTF_8).build().toString());
            response.setContentLengthLong(download.size());
            download.transferTo(response.getOutputStream());
        }
    }
}
