package com.sicost.b8;

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
import static com.sicost.b8.B8Dtos.*;

@RestController
public class B8Controller {
    private final B8Service service;
    private final ExcelExportService exports;
    public B8Controller(B8Service service,ExcelExportService exports){this.service=service;this.exports=exports;}
    @GetMapping("/api/excel/B8/template")
    public void getB8Template(HttpServletRequest request,HttpServletResponse response) throws IOException {
        allow(request.getParameterMap().keySet(),Set.of());
        var columns=new ArrayList<ExcelExportService.Column>();
        for(int i=0;i<B8ImportDefinition.HEADERS.size();i++)columns.add(new ExcelExportService.Column("b8Field"+i,B8ImportDefinition.HEADERS.get(i),ExcelExportService.CellType.TEXT));
        var specification=new ExcelExportService.Specification("개발자_등록양식",null,columns,"B8 신규 등록","원","UI-02");
        try(var download=exports.generateTemplate(specification,B8ImportDefinition.GUIDE,request)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(download.filename(),StandardCharsets.UTF_8).build().toString());
            response.setContentLengthLong(download.size());download.transferTo(response.getOutputStream());
        }
    }
    @PostMapping(value="/api/excel/B8/validate",consumes="multipart/form-data")
    public ApiResponse<Validation> createB8Validation(MultipartHttpServletRequest request) {
        multipart(request,Set.of("file"));return ApiResponse.ok(service.validate(request.getFile("file")));
    }
    @PostMapping(value="/api/excel/B8/apply",consumes="multipart/form-data")
    public ApiResponse<Applied> saveB8Apply(MultipartHttpServletRequest request) {
        multipart(request,Set.of("file","uploadId"));String value=request.getParameter("uploadId");
        if(value==null||!value.matches("[1-9][0-9]{0,11}"))throw InputValidator.invalid("uploadId");
        return ApiResponse.ok(service.apply(request.getFile("file"),Long.parseLong(value)));
    }
    private void multipart(MultipartHttpServletRequest request,Set<String> allowed) {
        if(request.getQueryString()!=null)throw new ApiException(400,"UNKNOWN_FIELD","쿼리 항목은 허용되지 않습니다.","query",null);
        allow(request.getParameterMap().keySet(),allowed);allow(request.getMultiFileMap().keySet(),Set.of("file"));
        for(String key:request.getParameterMap().keySet())if(request.getParameterValues(key).length!=1)throw InputValidator.invalid(key);
        if(request.getFiles("file").size()!=1||request.getParameterMap().containsKey("file"))throw InputValidator.invalid("file");
    }
    private void allow(Set<String> keys,Set<String> allowed) {
        keys.forEach(key->{if(!allowed.contains(key))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",key,null);});
    }
}
