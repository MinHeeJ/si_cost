package com.sicost.b1;

import com.sicost.common.api.ApiResponse;
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
import static com.sicost.b1.B1Dtos.*;

@RestController
public class B1Controller {
    private final B1Service service;
    private final ExcelExportService exports;
    public B1Controller(B1Service service,ExcelExportService exports) { this.service=service;this.exports=exports; }
    @GetMapping("/api/excel/B1/template")
    public void getB1Template(HttpServletRequest request,HttpServletResponse response) throws IOException {
        allow(request.getParameterMap().keySet(),Set.of());
        if(request.getInputStream().read()!=-1)throw ProjectInputPolicy.invalid("body");
        List<ExcelExportService.Column> columns=new ArrayList<>();
        for(int i=0;i<B1ImportDefinition.HEADERS.size();i++)columns.add(new ExcelExportService.Column("payrollField"+i,B1ImportDefinition.HEADERS.get(i)+(i==0?"*":""),i<2?ExcelExportService.CellType.TEXT:ExcelExportService.CellType.NUMBER));
        var spec=new ExcelExportService.Specification("급여_등록양식",null,columns,"B1","원","UI-02");
        try(var download=exports.generateTemplate(spec,List.of(
            "첫 시트에 1~5,000행을 입력해 주세요. 관리번호는 필수이며 중복할 수 없습니다.",
            "관리번호는 텍스트로 입력하고 앞자리 0과 대소문자를 보존하세요. 이름은 빈칸 허용, 입력하면 현재 이름과 같아야 합니다.",
            "급여·보험·세금·외주 공급가는 원 단위 정수입니다. 직원구분과 파생값은 현재 서버 기준으로 계산합니다.",
            "대상월은 화면에서 선택합니다. 선택월 전체를 교체하며 파일에서 빠진 인력의 해당월 급여는 제거됩니다.",
            "검사 후 같은 파일과 업로드 번호로 전량교체를 확인해 주세요. 다른월·개발자·가격·상태는 변경하지 않습니다."),request)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(download.filename(),StandardCharsets.UTF_8).build().toString());
            response.setContentLengthLong(download.size());download.transferTo(response.getOutputStream());
        }
    }
    @PostMapping(value="/api/excel/B1/validate",consumes="multipart/form-data")
    public ApiResponse<Object> createB1Validation(MultipartHttpServletRequest request) {
        multipart(request,Set.of("file","month","uploadId"));
        return ApiResponse.ok(service.validate(request.getFile("file"),new Request(request.getParameter("month"),id(request.getParameter("uploadId"),false),null,false)));
    }
    @PostMapping(value="/api/excel/B1/apply",consumes="multipart/form-data")
    public ApiResponse<Result> createB1Apply(MultipartHttpServletRequest request) {
        multipart(request,Set.of("file","month","uploadId","expectedRevision","confirmFullReplacement"));
        if(!"true".equals(request.getParameter("confirmFullReplacement")))throw ProjectInputPolicy.invalid("confirmFullReplacement");
        return ApiResponse.ok(service.apply(request.getFile("file"),new Request(request.getParameter("month"),id(request.getParameter("uploadId"),true),request.getParameter("expectedRevision"),true)));
    }
    private Long id(String value,boolean required) {
        if(value==null&&!required)return null;
        if(value==null||!value.strip().matches("[1-9][0-9]{0,11}"))throw ProjectInputPolicy.invalid("uploadId");return Long.valueOf(value.strip());
    }
    private void multipart(MultipartHttpServletRequest request,Set<String> allowed) {
        if(request.getQueryString()!=null)throw ProjectInputPolicy.invalid("query");
        allow(request.getParameterMap().keySet(),allowed);allow(request.getMultiFileMap().keySet(),Set.of("file"));
        for(String key:request.getParameterMap().keySet())if(request.getParameterValues(key).length!=1)throw ProjectInputPolicy.invalid(key);
        if(request.getFiles("file").size()!=1||request.getParameterMap().containsKey("file"))throw ProjectInputPolicy.invalid("file");
    }
    private void allow(Set<String> keys,Set<String> allowed) { keys.forEach(k->{if(!allowed.contains(k))throw ProjectInputPolicy.invalid(k);}); }
}
