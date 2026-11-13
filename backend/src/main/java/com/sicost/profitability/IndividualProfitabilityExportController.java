package com.sicost.profitability;

import com.sicost.common.validation.ProjectInputPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
public class IndividualProfitabilityExportController {
    private final IndividualProfitabilityExportService service;
    public IndividualProfitabilityExportController(IndividualProfitabilityExportService service) { this.service=service; }
    @GetMapping("/api/individual-profitability/export")
    public void getIndividualProfitabilityExport(@RequestParam MultiValueMap<String,String> query,
        @RequestBody(required=false) String body, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if(body!=null && !body.isBlank()) throw ProjectInputPolicy.invalid("body");
        // No headers/stream access before the shared service has committed the mandatory audit.
        try(var download=service.create(IndividualProfitabilityExportInput.parse(query),request)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(download.filename(),StandardCharsets.UTF_8).build().toString());
            response.setHeader("Cache-Control","no-store");
            response.setContentLengthLong(download.size());
            download.transferTo(response.getOutputStream());
        }
    }
}
