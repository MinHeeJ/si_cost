package com.sicost.b7;

import com.sicost.common.api.*;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.validation.InputValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.ContentDisposition;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import static com.sicost.b7.B7Dtos.*;

@RestController
public class B7Controller {
    private final B7Service service;
    private final ExcelExportService exports;
    public B7Controller(B7Service service,ExcelExportService exports){this.service=service;this.exports=exports;}
    @GetMapping("/api/excel/B7/template")
    public void getB7Template(HttpServletRequest request,HttpServletResponse response) throws IOException {
        allow(request.getParameterMap().keySet(),Set.of());
        var columns=new ArrayList<ExcelExportService.Column>();
        for(int i=0;i<ExcelImportService.HEADERS.size();i++) columns.add(new ExcelExportService.Column("b7Field"+i,ExcelImportService.HEADERS.get(i),ExcelExportService.CellType.TEXT));
        try(var download=exports.generateTemplate(columns,List.of(
            "첫 시트에 1~5,000행을 입력해 주세요. 구분·상호·사업자등록번호는 필수입니다.",
            "구분은 CLNT_TYPE, 업종은 INDUTY, 은행은 BANK의 활성 코드명을 입력하세요. 코드ID가 아닙니다.",
            "고객사는 세금계산서 이메일 필수, 협력사는 은행·계좌번호·예금주 필수, 고객·협력은 모두 필수입니다.",
            "사업자등록번호·연락처·계좌번호는 텍스트 형식으로 입력하세요. 앞자리 0과 하이픈을 보존하세요.",
            "기존 사업자등록번호는 등록할 수 없습니다. 검사 후 같은 파일로 전량 반영해 주세요."),request)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(download.filename(),StandardCharsets.UTF_8).build().toString());
            response.setContentLengthLong(download.size());download.transferTo(response.getOutputStream());
        }
    }
    @PostMapping(value="/api/excel/B7/validate",consumes="multipart/form-data")
    public ApiResponse<Validation> createB7Validation(MultipartHttpServletRequest request) {
        multipart(request,Set.of("file"));
        return ApiResponse.ok(service.validate(request.getFile("file")));
    }
    @PostMapping(value="/api/excel/B7/apply",consumes="multipart/form-data")
    public ApiResponse<Applied> saveB7Apply(MultipartHttpServletRequest request) {
        multipart(request,Set.of("file","uploadId"));
        String value=request.getParameter("uploadId");
        if(value==null||!value.matches("[1-9][0-9]{0,11}"))throw InputValidator.invalid("uploadId");
        return ApiResponse.ok(service.apply(request.getFile("file"),Long.parseLong(value)));
    }
    private void multipart(MultipartHttpServletRequest request,Set<String> allowed) {
        if(request.getQueryString()!=null)throw new ApiException(400,"UNKNOWN_FIELD","쿼리 항목은 허용되지 않습니다.","query",null);
        allow(request.getParameterMap().keySet(),allowed);allow(request.getMultiFileMap().keySet(),Set.of("file"));
        for(String key:request.getParameterMap().keySet()) if(request.getParameterValues(key).length!=1)throw InputValidator.invalid(key);
        if(request.getFiles("file").size()!=1)throw InputValidator.invalid("file");
        if(request.getParameterMap().containsKey("file"))throw InputValidator.invalid("file");
    }
    private void allow(Set<String> keys,Set<String> allowed) {
        keys.forEach(key->{if(!allowed.contains(key))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",key,null);});
    }
}
