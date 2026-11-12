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
    private final PeopleExportService people;
    private BillingReviewExportService billingReviews;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void billingReviews(BillingReviewExportService billingReviews) { this.billingReviews = billingReviews; }
    public MasterDataExportController(MasterDataExportService service, PeopleExportService people) { this.service = service; this.people = people; }
    @PostMapping("/api/exports/master-data")
    public void createMasterDataExport(@RequestBody JsonNode body, @RequestParam Map<String, String> query,
                                       HttpServletRequest request, HttpServletResponse response) throws IOException {
        query.keySet().forEach(field -> { throw MasterDataExportInput.unknown(field); });
        // 개발자·후보 목록(UI-02/UI-33)은 각 목록 서비스를 재사용하고, 거래처·영업 파트는 기존 경로를 그대로 쓴다.
        try (var download = BillingReviewExportService.handles(body) ? billingReviews.create(body, request) : PeopleExportInput.handles(body) ? people.create(body, request) : service.create(MasterDataExportInput.parse(body), request)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", ContentDisposition.attachment().filename(download.filename(), StandardCharsets.UTF_8).build().toString());
            response.setHeader("Cache-Control", "no-store");
            response.setContentLengthLong(download.size());
            download.transferTo(response.getOutputStream());
        }
    }
}
