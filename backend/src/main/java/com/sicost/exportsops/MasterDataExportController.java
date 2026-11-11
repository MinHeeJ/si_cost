package com.sicost.exportsops;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.web.bind.annotation.*;

@RestController
public class MasterDataExportController {
    private final MasterDataExportService service;
    public MasterDataExportController(MasterDataExportService service) { this.service = service; }
    @PostMapping("/api/exports/master-data")
    public void createMasterDataExport(@RequestBody JsonNode body, @RequestParam Map<String, String> query,
                                       HttpServletRequest request, HttpServletResponse response) throws IOException {
        query.keySet().forEach(field -> { throw MasterDataExportInput.unknown(field); });
        try (var download = service.create(MasterDataExportInput.parse(body), request)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", ContentDisposition.attachment().filename(download.filename(), StandardCharsets.UTF_8).build().toString());
            response.setHeader("Cache-Control", "no-store");
            response.setContentLengthLong(download.size());
            download.transferTo(response.getOutputStream());
        }
    }
}
