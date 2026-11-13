package com.sicost.b2;

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
import static com.sicost.b2.FinancialImportDtos.*;

@RestController
public class FinancialImportController {
    private final FinancialImportService service;
    private final ExcelExportService exports;
    public FinancialImportController(FinancialImportService service,ExcelExportService exports) { this.service=service;this.exports=exports; }
    @GetMapping("/api/excel/B2/template")
    public void getB2Template(HttpServletRequest request,HttpServletResponse response)throws IOException {
        allow(request.getParameterMap().keySet(),Set.of("month"));single(request);String month=request.getParameter("month");service.month(month);
        if(request.getInputStream().read()!=-1)throw ProjectInputPolicy.invalid("body");
        List<ExcelExportService.Column> columns=new ArrayList<>();
        for(int i=0;i<FinancialImportDefinition.HEADERS.size();i++)columns.add(new ExcelExportService.Column("financialField"+i,
            FinancialImportDefinition.HEADERS.get(i)+(Set.of(0,2,3).contains(i)?"*":""),i==3||i==4?ExcelExportService.CellType.NUMBER:ExcelExportService.CellType.TEXT));
        var spec=new ExcelExportService.Specification("재무_등록양식",null,columns,"B2 · 대상월 "+month,"원","UI-10");
        try(var download=exports.generateTemplate(spec,List.of(
            "첫 시트 8열, 1~5,000행. 구분·세부 항목·금액은 필수입니다. 제목에 번호를 붙이지 마세요.",
            "구분과 판관비 항목은 사용 중인 코드 이름 또는 저장된 표기 대응만 사용합니다. 세부 항목은 독립된 입력값입니다.",
            "금액과 부가세는 원 정수입니다. 부가세 빈칸은 0, 입출금일 빈칸은 없음, 매달 반복 빈칸은 N입니다.",
            "검사 후 같은 파일과 업로드 번호로 선택월의 업로드 항목만 교체합니다. 직접 입력·자동 채움·지난달 복사 항목은 보존합니다."),request)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(download.filename(),StandardCharsets.UTF_8).build().toString());
            response.setContentLengthLong(download.size());download.transferTo(response.getOutputStream());
        }
    }
    @PostMapping(value="/api/excel/B2/validate",consumes="multipart/form-data")
    public ApiResponse<Result> createB2Validation(MultipartHttpServletRequest request) {
        multipart(request,Set.of("file","month"));return ApiResponse.ok(service.validate(request.getFile("file"),request.getParameter("month")));
    }
    @PostMapping(value="/api/excel/B2/apply",consumes="multipart/form-data")
    public ApiResponse<Result> saveB2Apply(MultipartHttpServletRequest request) {
        multipart(request,Set.of("file","month","uploadId","expectedRevision"));
        return ApiResponse.ok(service.apply(request.getFile("file"),new Request(request.getParameter("month"),request.getParameter("uploadId"),request.getParameter("expectedRevision"))));
    }
    private void multipart(MultipartHttpServletRequest request,Set<String> allowed) {
        if(request.getQueryString()!=null)throw ProjectInputPolicy.invalid("query");
        allow(request.getParameterMap().keySet(),allowed);allow(request.getMultiFileMap().keySet(),Set.of("file"));single(request);
        if(request.getFiles("file").size()!=1||request.getParameterMap().containsKey("file"))throw ProjectInputPolicy.invalid("file");
    }
    private void single(HttpServletRequest request) { for(String key:request.getParameterMap().keySet())if(request.getParameterValues(key).length!=1)throw ProjectInputPolicy.invalid(key); }
    private void allow(Set<String> keys,Set<String> allowed) {
        keys.forEach(k->{if(!allowed.contains(k))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 항목입니다.",k,null);});
    }
}
