package com.sicost.b6;

import com.sicost.b9.B9Dtos.Result;
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
import static com.sicost.b6.B6Dtos.*;

@RestController
public class B6Controller {
    private final B6Service service;
    private final ExcelExportService exports;
    public B6Controller(B6Service service,ExcelExportService exports) {this.service=service;this.exports=exports;}
    @GetMapping("/api/excel/B6/template")
    public void getB6Template(HttpServletRequest request,HttpServletResponse response) throws IOException {
        allow(request.getParameterMap().keySet(),Set.of("projectId"));single(request);
        var project=service.project(request.getParameter("projectId"));var columns=new ArrayList<ExcelExportService.Column>();
        var headers=B6ImportDefinition.headers(project);
        for(int i=0;i<headers.size();i++)columns.add(new ExcelExportService.Column("b6Field"+i,headers.get(i),ExcelExportService.CellType.TEXT));
        var specification=new ExcelExportService.Specification("턴키배분표_등록양식",null,columns,"B6 · "+project.id(),"원","UI-01");
        try(var download=exports.generateTemplate(specification,B6ImportDefinition.GUIDE,request)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(download.filename(),StandardCharsets.UTF_8).build().toString());
            response.setContentLengthLong(download.size());download.transferTo(response.getOutputStream());
        }
    }
    @PostMapping(value="/api/excel/B6/validate",consumes="multipart/form-data")
    public ApiResponse<Validation> createB6Validation(MultipartHttpServletRequest request) {
        multipart(request,Set.of("file","year","projectId","step"));return ApiResponse.ok(service.validate(request.getFile("file"),input(request,false)));
    }
    @PostMapping(value="/api/excel/B6/apply",consumes="multipart/form-data")
    public ApiResponse<Result> saveB6Apply(MultipartHttpServletRequest request) {
        multipart(request,Set.of("file","year","projectId","step","uploadId","expectedRevision"));
        return ApiResponse.ok(service.apply(request.getFile("file"),input(request,true)));
    }
    private Request input(MultipartHttpServletRequest request,boolean apply) {
        String year=request.getParameter("year");if(year==null||!year.matches("[0-9]{4}"))throw ProjectInputPolicy.invalid("year");
        String projectId=ProjectInputPolicy.text(request.getParameter("projectId"),20,"projectId"),step=request.getParameter("step");
        if(step==null||!Set.of("EST","CTRT","PLAN").contains(step))throw ProjectInputPolicy.invalid("step");
        Long id=null;String revision=null;
        if(apply) {
            String value=request.getParameter("uploadId");if(value==null||!value.matches("[1-9][0-9]{0,18}"))throw ProjectInputPolicy.invalid("uploadId");
            try{id=Long.parseLong(value);}catch(NumberFormatException e){throw ProjectInputPolicy.invalid("uploadId");}
            revision=ProjectInputPolicy.expectedRevision(request.getParameter("expectedRevision"));
        }
        return new Request(Integer.parseInt(year),projectId,step,id,revision);
    }
    private void multipart(MultipartHttpServletRequest request,Set<String> allowed) {
        if(request.getQueryString()!=null)throw ProjectInputPolicy.invalid("query");
        allow(request.getParameterMap().keySet(),allowed);allow(request.getMultiFileMap().keySet(),Set.of("file"));single(request);
        if(request.getFiles("file").size()!=1||request.getParameterMap().containsKey("file"))throw ProjectInputPolicy.invalid("file");
    }
    private void single(HttpServletRequest request) {for(String key:request.getParameterMap().keySet())if(request.getParameterValues(key).length!=1)throw ProjectInputPolicy.invalid(key);}
    private void allow(Set<String> keys,Set<String> allowed) {keys.forEach(key->{if(!allowed.contains(key))throw ProjectInputPolicy.invalid(key);});}
}
