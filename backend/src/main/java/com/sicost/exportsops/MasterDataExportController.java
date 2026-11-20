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
    private final AssignmentExportService assignments;
    private com.sicost.permission.PermissionService permissions;
    @org.springframework.beans.factory.annotation.Autowired
    public void setPermissions(com.sicost.permission.PermissionService permissions) {this.permissions=permissions;}
    private BillingReviewExportService billingReviews;
    private SettlementExportService settlements;
    private ProjectExportService projects;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void projects(ProjectExportService projects) { this.projects = projects; }
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void settlements(SettlementExportService settlements) { this.settlements = settlements; }
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void billingReviews(BillingReviewExportService billingReviews) { this.billingReviews = billingReviews; }
    public MasterDataExportController(MasterDataExportService service, PeopleExportService people, AssignmentExportService assignments) { this.service = service; this.people = people; this.assignments = assignments; }
    @PostMapping("/api/exports/master-data")
    public void createMasterDataExport(@RequestBody JsonNode body, @RequestParam Map<String, String> query,
                                       HttpServletRequest request, HttpServletResponse response) throws IOException {
        query.keySet().forEach(field -> { throw MasterDataExportInput.unknown(field); });
        if(permissions!=null) {
            String screen=switch(body==null?"":body.path("view").asText()) {
                case "CLIENTS","PARTS" -> "UI-15";
                case "DEVELOPERS" -> "UI-02";
                case "CANDIDATES" -> "UI-33";
                case "PROJECTS" -> "UI-01";
                case "ASSIGNMENTS" -> "UI-12";
                case "BILLS","BILLING_CHECKS","RECEIVABLES" -> "UI-13";
                case "SALES_PART_SETTLEMENTS" -> "UI-19";
                case "RATE_RENEGOTIATIONS" -> "UI-32";
                default -> throw new com.sicost.common.api.ApiException(400,"INVALID_FIELD","다운로드 대상을 확인해 주세요.","view",null);
            };
            permissions.require(screen,"DNLD");

            request.setAttribute(com.sicost.permission.PermissionOperationRegistry.OPERATION,new com.sicost.permission.PermissionOperationRegistry.Operation("POST","/api/exports/master-data",screen,"DNLD","MasterDataExportController.createMasterDataExport"));
        }
        // 개발자·후보 목록(UI-02/UI-33)은 각 목록 서비스를 재사용하고, 거래처·영업 파트는 기존 경로를 그대로 쓴다.
        try (var download = ProjectExportService.handles(body) ? projects.create(body, request) : SettlementExportService.handles(body) ? settlements.create(body, request) : AssignmentExportService.handles(body) ? assignments.create(body, request) : BillingReviewExportService.handles(body) ? billingReviews.create(body, request) : PeopleExportInput.handles(body) ? people.create(body, request) : service.create(MasterDataExportInput.parse(body), request)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", ContentDisposition.attachment().filename(download.filename(), StandardCharsets.UTF_8).build().toString());
            response.setHeader("Cache-Control", "no-store");
            response.setContentLengthLong(download.size());
            download.transferTo(response.getOutputStream());
        }
    }
}
