package com.sicost.permission;

import com.sicost.common.api.ApiException;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/** Exact method + Spring's server-owned full mapping. No client screen/Referer inference. */
@Component
public class PermissionOperationRegistry implements HandlerInterceptor {
    public static final String OPERATION=PermissionOperationRegistry.class.getName()+".operation";
    public static final String ACCEPTED=PermissionOperationRegistry.class.getName()+".accepted-capabilities";
    public record Operation(String method,String path,String policy,String action,String handler) {}
    private final Map<String,Operation> operations;
    private final PermissionService permissions;
    private PermissionRowPolicy rows;
    @org.springframework.beans.factory.annotation.Autowired
    public void setRows(PermissionRowPolicy rows) {this.rows=rows;}
    public PermissionOperationRegistry(PermissionService permissions) throws IOException {
        this.permissions=permissions;Map<String,Operation> registry=new LinkedHashMap<>();
        try(var reader=new BufferedReader(new InputStreamReader(new ClassPathResource("permission-operations.tsv").getInputStream(),StandardCharsets.UTF_8))) {
            for(String line:reader.lines().toList()) {
                if(line.isBlank() || line.startsWith("#"))continue;
                String[] fields=line.split("\t");
                if(fields.length!=5)throw new IllegalStateException("잘못된 권한 등록입니다.");
                var operation=new Operation(fields[0],fields[1],fields[2],fields[3],fields[4]);
                if(registry.put(operation.method()+" "+operation.path(),operation)!=null)throw new IllegalStateException("중복 권한 등록입니다.");
            }
        }
        operations=Collections.unmodifiableMap(registry);
    }
    public Collection<Operation> operations() {return operations.values();}
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
        if(!request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/"))return true;
        if(!(handler instanceof HandlerMethod method))throw PermissionService.denied();
        String path=String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        var operation=operations.get(request.getMethod()+" "+path);
        if(operation==null || !operation.handler().equals(method.getBeanType().getSimpleName()+"."+method.getMethod().getName()))throw PermissionService.denied();
        request.setAttribute(OPERATION,operation);
        if(Set.of("PUBLIC","AUTH").contains(operation.policy()))return true;
        permissions.userId();
        if(request.getAttribute(ACCEPTED)==null)request.setAttribute(ACCEPTED,permissions.capabilities());
        if("OWN".equals(operation.policy())) {if(path.equals("/api/automation-runs")) {permissions.requireRole("ADMIN");permissions.requireAll();}return true;}
        if("DYNAMIC".equals(operation.policy())) {
            dynamic(request,path);rows.check(request,null);return true;
        }
        if("INTERNAL".equals(operation.policy())) {permissions.requireRole("ADMIN");permissions.requireAll();return true;}
        if(!operation.policy().startsWith("UI-"))throw PermissionService.denied();
        boolean sharedProject=path.equals("/api/projects/{id}/progress") && request.getMethod().equals("GET")
            || path.equals("/api/projects/{id}/progress/history")
            || path.equals("/api/projects/{id}/issues") || path.equals("/api/projects/{id}/issues/{issueId}")
            || path.equals("/api/projects/{id}/progress/week-memos") || path.equals("/api/projects/{id}/progress/week-memos/{memoSn}");
        if(sharedProject) {
            String screen=permissions.allows("UI-01",operation.action())?"UI-01":"UI-31";
            permissions.require(screen,operation.action());
            Object variables=request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            String id=variables instanceof Map<?,?> values && values.get("id") instanceof String value?value:null;
            permissions.requireCurrentProject(id);
            request.setAttribute(OPERATION,new Operation(operation.method(),operation.path(),screen,operation.action(),operation.handler()));
            return true;
        }
        permissions.require(operation.policy(),operation.action());
        rows.check(request,request.getAttribute(PermissionBusinessPolicy.BODY) instanceof com.fasterxml.jackson.databind.JsonNode body?body:null);
        if(path.startsWith("/api/assignment-candidates/{id}/"))permissions.require("UI-29","UPD");
        if(operation.action().equals("UPLD")) {
            permissions.requireRole("ADMIN","FIN");
            if(path.contains("/B1/") || Set.of("UI-08","UI-10","UI-18").contains(operation.policy())) {permissions.requireAll();permissions.requireSensitive();}
            if(path.contains("/B8/"))permissions.requireSensitive();
            if(request.getParameter("uploadId")!=null)permissions.requireUpload(request.getParameter("uploadId"),"UPLD");
        }
        if(Set.of("UI-24","UI-25").contains(operation.policy()))permissions.requireRole("ADMIN");
        if(path.startsWith("/api/sales-parts") && !"GET".equals(operation.method()))permissions.requireRole("ADMIN","FIN");
        if(path.equals("/api/projects/{id}/plans") && operation.action().equals("UPD")) {
            Object variables=request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            permissions.requireProjectPlanner(variables instanceof Map<?,?> values?(String)values.get("id"):null);
        }
        if(Set.of("UI-07","UI-08","UI-09","UI-10","UI-14","UI-18","UI-21").contains(operation.policy()))permissions.requireAll();
        if(path.endsWith("/reopen") || path.endsWith("/reopen-preview") || path.endsWith("/access-logs") || operation.handler().equals("RoleStopController.saveRoleStop"))permissions.requireRole("ADMIN");
        if(path.endsWith("/account")) {permissions.requireRole("ADMIN","FIN");permissions.requireSensitive();}
        if((path.endsWith("/confirm") || path.endsWith("/cancel")) && operation.policy().equals("UI-19"))permissions.requireRole("ADMIN","FIN");

        if(!operation.policy().equals("UI-32") && !permissions.capabilities().sensitive() && request.getParameter("sort")!=null && request.getParameter("sort").matches("(?i).*(pay|salary|cost|profit|margin|insurance|retirement).*") || !permissions.candidateRateAllowed() && Set.of("monthlyRate","billingUnitPrice").contains(request.getParameter("sort")==null?"":request.getParameter("sort")))
            throw new ApiException(400,"FIELD_NOT_EDITABLE","조회할 수 없는 항목으로 정렬할 수 없습니다.","sort",null);
        if(operation.policy().equals("UI-19") && !permissions.capabilities().sensitive() && request.getParameter("sort")!=null && !Set.of("name","mm","unitPrice").contains(request.getParameter("sort")))
            throw new ApiException(400,"FIELD_NOT_EDITABLE","조회할 수 없는 항목으로 정렬할 수 없습니다.","sort",null);
        if(operation.policy().equals("UI-32") && !permissions.capabilities().sensitive() && "monthlyLaborCost".equals(request.getParameter("sort")))
            throw new ApiException(400,"FIELD_NOT_EDITABLE","조회할 수 없는 항목으로 정렬할 수 없습니다.","sort",null);
        if(operation.policy().equals("UI-32") && request.getParameter("projectId")!=null)permissions.requireCurrentProject(request.getParameter("projectId"));
        if(operation.policy().equals("UI-31") && request.getParameter("projectId")!=null)permissions.requireCurrentProject(request.getParameter("projectId"));
        return true;
    }
    private void dynamic(HttpServletRequest request,String path) {
        if(path.equals("/api/financial-history")) {
            String screen=switch(Objects.toString(request.getParameter("scope"),"")) {
                case "MONTH","CARDS" -> "UI-10";
                case "TAX" -> "UI-09";
                case "BUSINESS_PLAN" -> "UI-18";
                case "HISTORICAL_PROFIT_LOSS" -> "UI-08";
                case "PART_SETTLEMENT" -> "UI-19";
                default -> throw com.sicost.common.validation.InputValidator.invalid("scope");
            };
            permissions.require(screen,"INQ");if(!screen.equals("UI-19"))permissions.requireAll();
            if(screen.equals("UI-19") && (request.getParameter("partId")==null || request.getParameter("partId").isBlank()))throw com.sicost.common.validation.InputValidator.invalid("partId");
            targetPolicy(request,screen);
            if(request.getParameter("uploadId")!=null)permissions.requireUpload(request.getParameter("uploadId"),"INQ");
            return;
        }
        if(path.equals("/api/assignment-people/options")) {minimumInput(request,List.of("UI-12"));return;}
        if(path.startsWith("/api/uploads/{id}/")) {
            Object variables=request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            String id=variables instanceof Map<?,?> values?(String)values.get("id"):null;
            var operation=(Operation)request.getAttribute(OPERATION);
            String screen=permissions.requireUpload(id,operation.action());targetPolicy(request,screen);
            request.setAttribute(OPERATION,new Operation(operation.method(),operation.path(),screen,operation.action(),operation.handler()));return;
        }
        if(path.equals("/api/business-users/options")) {
            String purpose=request.getParameter("purpose");
            if(!Set.of("PM","PART_LEADER").contains(purpose==null?"":purpose))throw com.sicost.common.validation.InputValidator.invalid("purpose");
            if(permissions.allows("UI-23","INQ")) {targetPolicy(request,"UI-23");return;}
            minimumInput(request,purpose.equals("PART_LEADER")?List.of("UI-15"):List.of("UI-01"));
            if(purpose.equals("PART_LEADER"))permissions.requireRole("ADMIN","FIN");
            return;
        }
        if(path.equals("/api/clients/options")) {
            String purpose=request.getParameter("purpose");
            if(!Set.of("CUSTOMER","VENDOR").contains(purpose==null?"":purpose))throw com.sicost.common.validation.InputValidator.invalid("purpose");
            if(permissions.allows("UI-15","INQ")) {targetPolicy(request,"UI-15");return;}
            minimumInput(request,purpose.equals("CUSTOMER")?List.of("UI-01","UI-29"):List.of("UI-02","UI-33"));return;
        }
        if(path.equals("/api/sales-parts/options")) {
            if(permissions.allows("UI-15","INQ")) {targetPolicy(request,"UI-15");return;}
            if(permissions.allows("UI-31","INQ")) {targetPolicy(request,"UI-31");return;}
            minimumInput(request,List.of("UI-01","UI-02","UI-29"));return;
        }
        if(path.equals("/api/projects/options")) {
            String purpose=request.getParameter("purpose");
            String screen=switch(purpose==null?"":purpose) {
                case "ASSIGNMENT" -> "UI-12";
                case "BILLING" -> "UI-13";
                default -> throw com.sicost.common.validation.InputValidator.invalid("purpose");
            };
            minimumInput(request,List.of(screen));return;
        }
        if(path.equals("/api/projects/{id}/pm")) {
            if(permissions.allows("UI-23","INQ")) {targetPolicy(request,"UI-23");return;}
            String screen=permissions.allows("UI-01","INQ")?"UI-01":"UI-31";
            permissions.require(screen,"INQ");
            Object variables=request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            String id=variables instanceof Map<?,?> values && values.get("id") instanceof String value?value:null;
            permissions.requireCurrentProject(id);targetPolicy(request,screen);return;
        }
        if(path.equals("/api/master-history") || path.equals("/api/people-history")) {
            String type=request.getParameter("targetType");
            String screen=path.equals("/api/master-history") ? switch(type==null?"":type) {
                case "USER" -> "UI-23";
                case "ROLE" -> "UI-24";
                case "MENU" -> "UI-25";
                case "CLIENT","PART" -> "UI-15";
                default -> throw com.sicost.common.validation.InputValidator.invalid("targetType");
            } : switch(type==null?"":type) {
                case "DEVELOPER" -> "UI-02";
                case "CANDIDATE" -> "UI-33";
                default -> throw com.sicost.common.validation.InputValidator.invalid("targetType");
            };
            permissions.require(screen,"INQ");
            if(Set.of("UI-24","UI-25").contains(screen))permissions.requireRole("ADMIN");
            var original=(Operation)request.getAttribute(OPERATION);
            request.setAttribute(OPERATION,new Operation(original.method(),original.path(),screen,"INQ",original.handler()));
            return;
        }
        if(path.equals("/api/resume-extractions")) {
            String target=request.getParameter("targetType");
            if(!Set.of("DEVELOPER","CANDIDATE").contains(target==null?"":target))throw PermissionService.denied();
            permissions.require(target.equals("DEVELOPER")?"UI-02":"UI-33","REG");return;
        }
        if(Set.of("/api/current-user","/api/menus","/api/period-context").contains(path))return;
        if(path.equals("/api/common-code-history")) {permissions.require("UI-27","INQ");return;}
        // Month/status only: shared lock metadata, not the ALL-only close ledger/actions.
        if(path.equals("/api/month-close")) {if(permissions.capabilities().screens().isEmpty())throw PermissionService.denied();return;}
        if(path.startsWith("/api/codes/") || path.startsWith("/api/params/")) {
            if(permissions.capabilities().screens().isEmpty())throw PermissionService.denied();return;
        }
        // Body-dependent exports and table-key histories have their own advice; never authorize by a client header.
        if(path.equals("/api/exports/master-data"))return;
        throw new ApiException(503,"POLICY_INTEGRATION_REQUIRED","대상 업무의 권한 연결이 필요합니다.");
    }
    private void minimumInput(HttpServletRequest request,List<String> screens) {
        for(String screen:screens)if(permissions.allows(screen,"INQ") && (permissions.allows(screen,"REG") || permissions.allows(screen,"UPD"))) {
            targetPolicy(request,screen);return;
        }
        throw PermissionService.denied();
    }
    private void targetPolicy(HttpServletRequest request,String screen) {
        var original=(Operation)request.getAttribute(OPERATION);
        request.setAttribute(OPERATION,new Operation(original.method(),original.path(),screen,"INQ",original.handler()));
    }
}
