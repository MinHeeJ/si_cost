package com.sicost.permission;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.HandlerMapping;
import static com.sicost.permission.PermissionRowMapper.Entity;

/** Validate complete target/reference sets; never silently drop unauthorized batch members. */
@Service
public class PermissionRowPolicy {
    private final PermissionService policies;
    private final PermissionRowMapper rows;
    private final Clock clock;
    public PermissionRowPolicy(PermissionService policies,PermissionRowMapper rows,Clock clock) {this.policies=policies;this.rows=rows;this.clock=clock;}
    public void check(HttpServletRequest request,JsonNode body) {
        if(!(request.getAttribute(PermissionOperationRegistry.OPERATION) instanceof PermissionOperationRegistry.Operation op))return;
        if(!op.policy().startsWith("UI-") || Set.of("UI-23","UI-24","UI-25","UI-27").contains(op.policy()))return;
        String month=month(request.getParameter("month"),YearMonth.now(clock.withZone(TimeConfiguration.SEOUL)).toString().replace("-",""));
        if(request.getParameter("expenseMonth")!=null)month=month(request.getParameter("expenseMonth"),month);
        if(body!=null)for(String key:List.of("month","baseMonth","inputMonth","applyMonth","expenseMonth"))if(body.path(key).isTextual())month=month(body.path(key).asText(),month);
        Object variables=request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if(variables instanceof Map<?,?> values && values.get("expenseMonth") instanceof String value)month=month(value,month);
        String from=month,to=month,year=request.getParameter("year");
        if(request.getMethod().equals("GET") && request.getParameter("month")==null && year!=null && year.matches("[0-9]{4}")) {from=year+"01";to=year+"12";}
        var capabilities=policies.capabilities();var scope=new PermissionScopeSql.Scope(capabilities.scope().all(),capabilities.scope().partIds(),capabilities.scope().projectIds(),from,to,policies.userId());
        Entity root=root(op.path());
        if(op.path().equals("/api/financial-history") && op.policy().equals("UI-19"))require(Entity.PART,request.getParameter("partId"),scope);
        boolean deferred=body==null && !request.getMethod().equals("GET") && request.getContentType()!=null && request.getContentType().contains("json") && request.getContentLengthLong()!=0;
        if(variables instanceof Map<?,?> values) {
            if(root!=null && !deferred) {
                Object id=values.get("id");if(id==null)id=values.get("billId");if(id==null)id=values.get("personId");if(id==null)id=values.get("partId");
                if(id instanceof String value) {
                    var targetScope=root==Entity.DEVELOPER && !Set.of("INQ","DNLD").contains(op.action())?new PermissionScopeSql.Scope(scope.all(),scope.parts(),Set.of(),scope.fromMonth(),scope.toMonth(),scope.userId()):scope;
                    require(root,value,targetScope);
                }
            }
            if(!deferred && values.get("receiptId") instanceof String id)require(Entity.RECEIPT,id,scope);
        }
        if(body!=null)walk(body,root,scope,op,"");
        if(op.path().equals("/api/people-history") && "DEVELOPER".equals(request.getParameter("targetType")))require(Entity.DEVELOPER,request.getParameter("targetId"),scope);
        for(String field:List.of("projectId","developerId","opportunityId","assignmentId","billId")) {
            String value=request.getParameter(field);if(value!=null && !(field.equals("developerId") && Set.of("UI-12","UI-29","UI-30").contains(op.policy())))require(field(field),value,scope);
        }
    }
    private void walk(JsonNode node,Entity root,PermissionScopeSql.Scope inherited,PermissionOperationRegistry.Operation op,String parent) {
        if(node.isArray()) {for(var item:node)walk(item,root,inherited,op,parent);return;}
        if(!node.isObject())return;
        String month=inherited.fromMonth();for(String name:List.of("month","baseMonth","inputMonth","expenseMonth","payMonth"))if(node.path(name).isTextual())month=month(node.path(name).asText(),month);
        var scope=new PermissionScopeSql.Scope(inherited.all(),inherited.parts(),inherited.projects(),month,month,inherited.userId());
        node.fields().forEachRemaining(entry->{
            String key=entry.getKey();JsonNode value=entry.getValue();Entity entity=field(key);
            if(key.equals("id") && (parent.isEmpty() || Set.of("items","rows","targets","selected").contains(parent)))entity=root;
            if(key.equals("partId") && !op.policy().equals("UI-15") && value.isTextual() && !scope.all() && !scope.parts().contains(value.asText()))throw missing();
            if(entity!=null && value.isTextual() && !value.asText().isEmpty()) {
                // Selection for new assignments is not authority to modify/read private developer details.
                boolean selection=entity==Entity.DEVELOPER && Set.of("UI-12","UI-29","UI-30").contains(op.policy());
                if(!selection)require(entity,value.asText(),scope);
            }
            if(key.equals("ids") && root!=null && value.isArray())for(var id:value)if(id.isTextual())require(root,id.asText(),scope);
            if(value.isContainerNode())walk(value,root,scope,op,key);
        });
    }
    private void require(Entity entity,String id,PermissionScopeSql.Scope scope) {
        if((entity==Entity.RECEIPT || entity==Entity.TENTATIVE) && (id==null || !id.matches("[1-9][0-9]{0,18}")))throw com.sicost.common.validation.InputValidator.invalid("id");
        if(entity==Entity.PART && !scope.all() && !scope.parts().contains(id))throw missing();
        if(!rows.permitted(new PermissionRowMapper.Target(entity,id),scope))throw missing();
    }
    private static Entity field(String key) {return switch(key) {case "projectId","projId"->Entity.PROJECT;case "assignmentId","asgnId"->Entity.ASSIGNMENT;case "developerId","devId","personId","contractDeveloperId"->Entity.DEVELOPER;case "billId","originalBillId","sourceBillId"->Entity.BILL;case "receiptId"->Entity.RECEIPT;case "opportunityId","oppId"->Entity.OPPORTUNITY;default->null;};}
    private static Entity root(String path) {
        if(path.startsWith("/api/projects"))return Entity.PROJECT;
        if(path.startsWith("/api/assignments"))return Entity.ASSIGNMENT;
        if(path.startsWith("/api/assignment-candidates/{id}"))return Entity.TENTATIVE;
        if(path.startsWith("/api/bills") || path.startsWith("/api/receivables"))return Entity.BILL;
        if(path.startsWith("/api/developers") || path.startsWith("/api/individual-profitability"))return Entity.DEVELOPER;
        if(path.startsWith("/api/sales-opportunities"))return Entity.OPPORTUNITY;
        if(path.startsWith("/api/sales-part-settlements"))return Entity.PART;
        return null;
    }
    private static String month(String input,String fallback) {if(input==null)return fallback;String value=input.replace("-","");return value.matches("[0-9]{4}(0[1-9]|1[0-2])")?value:fallback;}
    private static ApiException missing() {return new ApiException(404,"NOT_FOUND","요청한 자료를 찾을 수 없습니다.");}
}
