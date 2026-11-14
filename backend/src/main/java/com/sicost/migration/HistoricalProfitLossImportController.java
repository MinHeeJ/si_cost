package com.sicost.migration;

import com.sicost.common.api.*;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.validation.ProjectInputPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.ContentDisposition;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import static com.sicost.migration.HistoricalProfitLossImportDtos.*;
import static com.sicost.migration.HistoricalProfitLossImportDefinition.*;

@RestController
public class HistoricalProfitLossImportController {
    private final HistoricalProfitLossImportService service;
    private final HistoricalProfitLossImportMapper mapper;
    private final ExcelExportService exports;
    public HistoricalProfitLossImportController(HistoricalProfitLossImportService service,HistoricalProfitLossImportMapper mapper,ExcelExportService exports) {
        this.service=service;this.mapper=mapper;this.exports=exports;
    }
    @GetMapping("/api/profit-loss/migration/imports/template")
    public void getHistoricalProfitLossImportTemplate(HttpServletRequest request,HttpServletResponse response)throws IOException {
        allow(request.getParameterMap().keySet(),Set.of());
        if(request.getInputStream().read()!=-1)throw ProjectInputPolicy.invalid("body");
        var columns=new ArrayList<ExcelExportService.Column>();
        for(int i=0;i<HEADERS.size();i++)columns.add(new ExcelExportService.Column("column"+i,HEADERS.get(i)+(i<5?"*":""),i==0?ExcelExportService.CellType.TEXT:ExcelExportService.CellType.NUMBER));
        String guide="B11 · BASE 전년 1월~직전월 · 사용하지 않을 연월 행은 삭제하세요. 필수 금액 빈칸은 0이 아닙니다. 선택 빈칸/–는 NULL입니다.";
        var spec=new ExcelExportService.Specification("이관손익_등록양식",null,columns,guide,"원","UI-08");
        try(var download=exports.generate(spec,sink->{
            for(String month:months(mapper.cashBase())) {List<Object> values=new ArrayList<>(Collections.nCopies(17,null));values.set(0,month);sink.accept(values);}
        },request)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(download.filename(),StandardCharsets.UTF_8).build().toString());
            response.setContentLengthLong(download.size());download.transferTo(response.getOutputStream());
        }
    }
    @PostMapping(value="/api/profit-loss/migration/imports/validate",consumes="multipart/form-data")
    public ApiResponse<Validation> createHistoricalProfitLossImportValidation(MultipartHttpServletRequest request) {
        multipart(request,Set.of("file"));return ApiResponse.ok(service.validate(request.getFile("file")));
    }
    @PostMapping(value="/api/profit-loss/migration/imports/apply",consumes="multipart/form-data")
    public ApiResponse<Applied> saveHistoricalProfitLossImport(MultipartHttpServletRequest request) {
        multipart(request,Set.of("file","uploadId","expectedRevision"));
        return ApiResponse.ok(service.apply(request.getFile("file"),new Request(request.getParameter("uploadId"),request.getParameter("expectedRevision"))));
    }
    private void multipart(MultipartHttpServletRequest request,Set<String> allowed) {
        if(request.getQueryString()!=null)throw ProjectInputPolicy.invalid("query");
        allow(request.getParameterMap().keySet(),allowed);allow(request.getMultiFileMap().keySet(),Set.of("file"));
        for(String key:request.getParameterMap().keySet())if(request.getParameterValues(key).length!=1)throw ProjectInputPolicy.invalid(key);
        if(request.getFiles("file").size()!=1||request.getParameterMap().containsKey("file"))throw ProjectInputPolicy.invalid("file");
    }
    private void allow(Set<String> keys,Set<String> allowed) {
        keys.forEach(k->{if(!allowed.contains(k))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 항목입니다.",k,null);});
    }
}
