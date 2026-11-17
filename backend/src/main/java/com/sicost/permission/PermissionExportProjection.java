package com.sicost.permission;

import com.sicost.authentication.AuthenticationTaskService;
import com.sicost.common.api.ApiException;
import com.sicost.common.excel.ExcelExportService.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.stereotype.Service;

/** Project columns AND their positional values before a workbook cell is ever created. */
@Service
public class PermissionExportProjection {
    private final PermissionProjectionService fields;
    private final PermissionService permissions;
    private final AuthenticationTaskService authentication;
    public PermissionExportProjection(PermissionProjectionService fields,PermissionService permissions,AuthenticationTaskService authentication) {this.fields=fields;this.permissions=permissions;this.authentication=authentication;}
    public record Plan(List<Column> columns,List<Integer> indexes) {
        public List<Object> values(List<Object> source) {List<Object> result=new ArrayList<>();for(int index:indexes)result.add(source.get(index));return result;}
    }
    public Plan plan(String screen,List<Column> columns) {
        List<Column> visible=new ArrayList<>();List<Integer> indexes=new ArrayList<>();
        for(int i=0;i<columns.size();i++)if(allowed(screen,columns.get(i).id(),columns.get(i).title())) {visible.add(columns.get(i));indexes.add(i);}
        if(visible.isEmpty())throw PermissionService.denied();
        return new Plan(List.copyOf(visible),List.copyOf(indexes));
    }
    public boolean allowed(String screen,String id,String title) {
        if(fields.sensitive())return true;
        if(screen.equals("UI-19"))return Set.of("name","projectNames","mm","unitPrice","profitStatus","month","personCount","surplusPersonCount","deficitPersonCount").contains(id);
        if(screen.equals("UI-32"))return permissions.capabilities().fields().get("UI-32").contains(id);
        if(screen.equals("UI-31") && Set.of("costUsage","costUsageRate","costUsageRatio","costUsagePercent").contains(id))return true;
        if(screen.equals("UI-03") && title.matches("(?s).*(인건비|노무비|팀비|프로젝트경비).*"))return false;
        return !fields.forbidden(screen,id) && !privateText(title) && (!id.equals("remark") || !Set.of("UI-03","UI-04").contains(screen));
    }
    public boolean privateMetadata(String screen,String text) {
        if(privateText(text))return true;
        if(text==null)return false;
        var names=java.util.regex.Pattern.compile("[A-Za-z][A-Za-z0-9_]*").matcher(text);
        while(names.find())if(fields.forbidden(screen,names.group()))return true;
        return false;
    }
    public boolean privateText(String text) {
        if(text==null)return false;
        return !fields.sensitive() && text.matches("(?is).*(급여|기본급|지급액|실지급|실수령|외주.?공급가|소득세|원천세|지급 계좌|계좌번호|은행|예금주|보험|퇴직|공제|개인.?원가|비용.?계|공통관리비|법인카드|이익금|이익.?금액|원가금액|인건비|노무비|노동비|계산.?근거|산식).*" )
            || !fields.billingRate() && text.matches("(?is).*(월.?단가|청구.?단가|적용.?단가|지급.?단가).*" );
    }
    public Runnable guard(Specification specification,HttpServletRequest request) {
        permissions.require(specification.screenId(),"DNLD");
        if(Set.of("UI-07","UI-08","UI-09","UI-10","UI-18","UI-21").contains(specification.screenId()))permissions.requireAll();
        var proof=authentication.capture(request);var scope=permissions.capabilities().scope();boolean sensitive=fields.sensitive(),rate=fields.billingRate();
        if(request.getAttribute(PermissionBusinessPolicy.BODY) instanceof com.fasterxml.jackson.databind.JsonNode body && body.path("sort").isTextual()) {
            String sort=body.path("sort").asText();boolean forbidden=specification.screenId().equals("UI-32")?!permissions.capabilities().fields().get("UI-32").contains(sort):fields.forbidden(specification.screenId(),sort);
            if(forbidden)throw new ApiException(400,"FIELD_NOT_EDITABLE","조회할 수 없는 항목으로 정렬할 수 없습니다.","sort",null);
        }
        return ()->{authentication.checkpoint(proof);permissions.require(specification.screenId(),"DNLD");
            if(!scope.equals(permissions.capabilities().scope()) || sensitive && !fields.sensitive() || rate && !fields.billingRate())throw PermissionService.denied();};
    }
}
