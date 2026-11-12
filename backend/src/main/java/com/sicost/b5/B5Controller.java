package com.sicost.b5;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.assignmentsops.AssignmentActionsDtos.Withdrawal;
import com.sicost.common.api.ApiResponse;
import com.sicost.common.excel.ExcelExportService;
import com.sicost.common.validation.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.ContentDisposition;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import static com.sicost.b5.B5Dtos.*;

@RestController
public class B5Controller {
    private final B5Service service;
    private final ExcelExportService exports;
    public B5Controller(B5Service service,ExcelExportService exports) {this.service=service;this.exports=exports;}
    @GetMapping("/api/excel/B5/template")
    public void getB5Template(HttpServletRequest request,HttpServletResponse response) throws IOException {
        allow(request.getParameterMap().keySet(),Set.of());if(request.getInputStream().read()!=-1)throw ProjectInputPolicy.invalid("body");
        List<ExcelExportService.Column> columns=new ArrayList<>();
        for(int i=0;i<B5ImportDefinition.HEADERS.size();i++) {String title=B5ImportDefinition.HEADERS.get(i);columns.add(new ExcelExportService.Column("assignmentField"+i,title+(B5ImportDefinition.REQUIRED.contains(title)?"*":""),i==5||i==6?ExcelExportService.CellType.NUMBER:ExcelExportService.CellType.TEXT));}
        var spec=new ExcelExportService.Specification("투입_신규양식",null,columns,"B5","원","UI-12");
        try(var file=exports.generateTemplate(spec,List.of(
            "신규 전용입니다. 연장·철수·단가·MM 수정은 변경 기록을 사용하세요.",
            "첫 시트 1~5,000행, XLSX 10MiB 이하. 프로젝트ID·관리번호·직무·투입일·철수일·투입 구분은 필수입니다.",
            "월 MM 빈칸은 1.00, 0.01~1.00 소수 최대 두 자리입니다. 날짜는 YYYY-MM-DD 또는 YYYY.MM.DD입니다.",
            "청구 단가는 원 정수입니다. 월단위·파견 유상만 적용합니다. 숨김 단가와 비대체 계약상 인력 입력은 경고 후 폐기합니다.",
            "투입 구분은 활성 코드명으로 입력합니다. 대체투입은 계약상 인력 관리번호가 필수이며 같은 프로젝트의 다른 인력이어야 합니다.",
            "연장 가능성은 높음/보통/낮음, 빈칸은 미정(NULL)입니다. 관리번호는 앞자리 0·대소문자를 보존하는 텍스트로 입력하세요.",
            "오류가 있으면 전파일을 반영하지 않습니다. 철수 선택을 바꾸면 같은 파일과 검사번호로 다시 검사하세요."),request)) {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(file.filename(),StandardCharsets.UTF_8).build().toString());response.setContentLengthLong(file.size());file.transferTo(response.getOutputStream());
        }
    }
    @PostMapping(value="/api/excel/B5/validate",consumes="multipart/form-data")
    public ApiResponse<Validation> createB5Validation(MultipartHttpServletRequest request,@RequestPart(name="withdrawalSelections",required=false) JsonNode selections) {
        multipart(request,Set.of("file","year","uploadId","withdrawalSelections"));
        return ApiResponse.ok(service.validate(request.getFile("file"),new Request(year(request.getParameter("year")),id(request.getParameter("uploadId"),false),null,selections(selections,false))));
    }
    @PostMapping(value="/api/excel/B5/apply",consumes="multipart/form-data")
    public ApiResponse<Result> createB5Apply(MultipartHttpServletRequest request,@RequestPart(name="withdrawalSelections",required=false) JsonNode selections) {
        multipart(request,Set.of("file","year","uploadId","previewToken","withdrawalSelections"));
        return ApiResponse.ok(service.apply(request.getFile("file"),new Request(year(request.getParameter("year")),id(request.getParameter("uploadId"),true),ProjectInputPolicy.text(request.getParameter("previewToken"),200,"previewToken"),selections(selections,true))));
    }
    private List<Selection> selections(JsonNode node,boolean required) {
        if(node==null){if(required)throw ProjectInputPolicy.invalid("withdrawalSelections");return List.of();}
        if(!node.isArray()||node.size()>5000)throw ProjectInputPolicy.invalid("withdrawalSelections");List<Selection> result=new ArrayList<>();Set<Integer> numbers=new HashSet<>();
        for(JsonNode row:node) {
            object(row,Set.of("rowNumber","withdrawals"));JsonNode number=row.get("rowNumber"),withdrawals=row.get("withdrawals");
            if(number==null||!number.isIntegralNumber()||!number.canConvertToInt()||number.intValue()<1||number.intValue()>5000||!numbers.add(number.intValue())||withdrawals==null||!withdrawals.isArray()||withdrawals.size()>5000)throw ProjectInputPolicy.invalid("withdrawalSelections");
            List<Withdrawal> values=new ArrayList<>();Set<String> ids=new HashSet<>();
            for(JsonNode w:withdrawals) {
                object(w,Set.of("assignmentId","expectedRevision","withdrawalDate","reasonCode","memo"));String id=field(w,"assignmentId",20,true);if(!ids.add(id))throw ProjectInputPolicy.invalid("withdrawalSelections");
                values.add(new Withdrawal(id,field(w,"expectedRevision",200,true),ProjectInputPolicy.date(field(w,"withdrawalDate",10,true),"withdrawalDate"),field(w,"reasonCode",10,true),field(w,"memo",200,false)));
            }
            result.add(new Selection(number.intValue(),List.copyOf(values)));
        }return List.copyOf(result);
    }
    private String field(JsonNode n,String field,int max,boolean required) {JsonNode v=n.get(field);if(v!=null&&!v.isTextual())throw ProjectInputPolicy.invalid(field);return required?ProjectInputPolicy.text(v==null?null:v.textValue(),max,field):ProjectInputPolicy.optionalText(v==null?null:v.textValue(),max,field);}
    private void object(JsonNode n,Set<String> fields) {if(!n.isObject())throw ProjectInputPolicy.invalid("withdrawalSelections");n.fieldNames().forEachRemaining(k->{if(!fields.contains(k))throw ProjectInputPolicy.invalid(k);});}
    private int year(String value) {if(value==null||!value.strip().matches("[0-9]{4}"))throw ProjectInputPolicy.invalid("year");return Integer.parseInt(value.strip());}
    private Long id(String value,boolean required) {if(value==null&&!required)return null;if(value==null||!value.strip().matches("[1-9][0-9]{0,11}"))throw ProjectInputPolicy.invalid("uploadId");return Long.valueOf(value.strip());}
    private void multipart(MultipartHttpServletRequest request,Set<String> fields) {
        if(request.getQueryString()!=null)throw ProjectInputPolicy.invalid("query");allow(request.getParameterMap().keySet(),fields);allow(request.getMultiFileMap().keySet(),Set.of("file","withdrawalSelections"));
        for(String key:request.getParameterMap().keySet())if(request.getParameterValues(key).length!=1||key.equals("withdrawalSelections"))throw ProjectInputPolicy.invalid(key);
        if(request.getFiles("file").size()!=1||request.getParameterMap().containsKey("file")||request.getFiles("withdrawalSelections").size()>1)throw ProjectInputPolicy.invalid("file");
    }
    private void allow(Set<String> fields,Set<String> allowed) {fields.forEach(k->{if(!allowed.contains(k))throw ProjectInputPolicy.invalid(k);});}
}
