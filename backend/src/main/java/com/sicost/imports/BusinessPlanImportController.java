package com.sicost.imports;

import com.sicost.common.api.*;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.validation.ProjectInputPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.*;
import org.springframework.http.ContentDisposition;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import static com.sicost.imports.BusinessPlanImportDtos.*;

@RestController
public class BusinessPlanImportController {
    private final BusinessPlanImportService service;
    private final ExcelExportService exports;
    public BusinessPlanImportController(BusinessPlanImportService service, ExcelExportService exports) { this.service = service; this.exports = exports; }
    @GetMapping("/api/business-plans/imports/template")
    public void getBusinessPlanImportTemplate(HttpServletRequest request, HttpServletResponse response) throws IOException {
        allow(request.getParameterMap().keySet(), Set.of("year")); single(request);
        int year = BusinessPlanImportService.year(request.getParameter("year"));
        if (request.getInputStream().read() != -1) throw ProjectInputPolicy.invalid("body");
        var columns = List.of(new ExcelExportService.Column("month", "연월*", ExcelExportService.CellType.TEXT),
            new ExcelExportService.Column("salesTarget", "매출 목표*", ExcelExportService.CellType.NUMBER),
            new ExcelExportService.Column("profitTarget", "영업이익 목표*", ExcelExportService.CellType.NUMBER));
        var specification = new ExcelExportService.Specification("사업계획_등록양식", null, columns, "B10 · 대상연도 " + year, "원", "UI-18");
        try (var download = exports.generate(specification, sink -> {
            for (int month = 1; month <= 12; month++) sink.accept(Arrays.asList(YearMonth.of(year, month).toString(), null, null));
        }, request)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", ContentDisposition.attachment().filename(download.filename(), StandardCharsets.UTF_8).build().toString());
            response.setContentLengthLong(download.size()); download.transferTo(response.getOutputStream());
        }
    }
    @PostMapping(value = "/api/business-plans/imports/validate", consumes = "multipart/form-data")
    public ApiResponse<Validation> createBusinessPlanImportValidation(MultipartHttpServletRequest request) {
        multipart(request, Set.of("file", "year"));
        return ApiResponse.ok(service.validate(request.getFile("file"), BusinessPlanImportService.year(request.getParameter("year"))));
    }
    @PostMapping(value = "/api/business-plans/imports/apply", consumes = "multipart/form-data")
    public ApiResponse<Applied> saveBusinessPlanImport(MultipartHttpServletRequest request) {
        multipart(request, Set.of("file", "year", "uploadId", "expectedRevision", "mode"));
        Request input = new Request(BusinessPlanImportService.year(request.getParameter("year")), request.getParameter("uploadId"),
            request.getParameter("expectedRevision"), request.getParameter("mode"));
        return ApiResponse.ok(service.apply(request.getFile("file"), input));
    }
    private void multipart(MultipartHttpServletRequest request, Set<String> allowed) {
        if (request.getQueryString() != null) throw ProjectInputPolicy.invalid("query");
        allow(request.getParameterMap().keySet(), allowed); allow(request.getMultiFileMap().keySet(), Set.of("file")); single(request);
        if (request.getFiles("file").size() != 1 || request.getParameterMap().containsKey("file")) throw ProjectInputPolicy.invalid("file");
    }
    private void single(HttpServletRequest request) {
        for (String key : request.getParameterMap().keySet()) if (request.getParameterValues(key).length != 1) throw ProjectInputPolicy.invalid(key);
    }
    private void allow(Set<String> keys, Set<String> allowed) {
        keys.forEach(k -> { if (!allowed.contains(k)) throw new ApiException(400, "UNKNOWN_FIELD", "허용되지 않은 항목입니다.", k, null); });
    }
}
