package com.sicost.permission;

import com.sicost.common.api.ApiException;
import com.sicost.common.TimeConfiguration;
import java.time.Clock;
import java.time.YearMonth;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {
    private final PermissionMapper mapper;
    private final Clock clock;
    private org.springframework.transaction.support.TransactionTemplate fresh;
    @org.springframework.beans.factory.annotation.Autowired
    public void setTransactions(org.springframework.transaction.PlatformTransactionManager manager) {
        fresh=new org.springframework.transaction.support.TransactionTemplate(manager);
        fresh.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }
    private <T> T latest(java.util.function.Supplier<T> read) {return fresh==null?read.get():fresh.execute(status->read.get());}
    public PermissionService(PermissionMapper mapper,Clock clock) {this.mapper=mapper;this.clock=clock;}
    public String userId() {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication==null || !authentication.isAuthenticated() || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
            throw new ApiException(401,"AUTH_REQUIRED","로그인이 필요합니다.");
        return authentication.getName();
    }
    public record Scope(boolean all,Set<String> partIds,Set<String> projectIds) {}
    public record Capabilities(Map<String,Set<String>> screens,Scope scope,boolean sensitive,Map<String,Set<String>> fields,Map<String,Boolean> features,Set<String> pcOnlyScreens) {}
    public Capabilities capabilities() {
        return PermissionAcceptanceContext.bound(capabilitiesFor(userId()));
    }
    Capabilities capabilitiesFor(String id) {
        return latest(()->loadCapabilities(id));
    }
    private Capabilities loadCapabilities(String id) {
        var roles=mapper.roles(id);var rows=mapper.menus(id);
        Map<String,PermissionMapper.Menu> indexed=new HashMap<>();rows.forEach(row->indexed.put(row.menuId(),row));
        Map<String,Set<String>> screens=new LinkedHashMap<>();
        Set<String> seen=new HashSet<>();
        var ordered=new ArrayList<>(rows);ordered.sort((left,right)->compareMenus(left,right,indexed));
        for(var row:ordered) {
            if(row.screenId()==null || !valid(row,indexed) || !seen.add(row.screenId()) || !row.inquiry())continue;
            Set<String> actions=new LinkedHashSet<>();actions.add("INQ");
            if(row.registration())actions.add("REG");if(row.update())actions.add("UPD");if(row.deletion())actions.add("DEL");
            if(row.upload())actions.add("UPLD");if(row.download())actions.add("DNLD");if(row.confirmation())actions.add("CNFM");
            screens.put(row.screenId(),Set.copyOf(actions));
        }
        boolean all=roles.stream().anyMatch(role->"ALL".equals(role.scope()));
        if(!all)for(String screen:Set.of("UI-07","UI-08","UI-09","UI-10","UI-14","UI-18","UI-21"))screens.remove(screen);
        if(roles.stream().noneMatch(role->role.roleId().equals("ADMIN"))) {screens.remove("UI-24");screens.remove("UI-25");}
        boolean part=roles.stream().anyMatch(role->"PART".equals(role.scope()));
        boolean project=roles.stream().anyMatch(role->"PROJ".equals(role.scope()));
        String month=YearMonth.now(clock.withZone(TimeConfiguration.SEOUL)).toString().replace("-","");
        var scope=new Scope(all,part?Set.copyOf(mapper.parts(id,month)):Set.of(),project?Set.copyOf(mapper.projects(id)):Set.of());
        Set<String> candidateFields=new HashSet<>(Set.of("id","name","employeeType","companyName","job","grade","skills","homeAddress","availableDate","contactStatus","lastContactDate","source","revision","companyId","phone","email","consent","jobCode","subJobCode","gradeCode","homeAreaCode","sourceCode","memo","consentUserId","consentDate","consentUserName","addressPath"));
        if(candidateRate(roles))candidateFields.add("desiredRate");
        boolean sensitive=roles.stream().anyMatch(role->"Y".equals(role.sensitive()));
        Set<String> pcOnlyScreens=new HashSet<>();
        for(var row:ordered)if(row.screenId()!=null && screens.containsKey(row.screenId()) && menuPath(row,indexed).stream().anyMatch(PermissionMapper.Menu::pcOnly))pcOnlyScreens.add(row.screenId());
        boolean administrator=roles.stream().anyMatch(role->"ADMIN".equals(role.roleId()));
        Set<String> renegotiationFields=new HashSet<>(Set.of("name","employeeType","projectName","renewalDate","contractMm","unitPrice","marginRate","targetUnitPrice","requiredIncreaseRate","annualGain"));
        if(sensitive)renegotiationFields.add("monthlyLaborCost");
        Set<String> developerFields=new HashSet<>(Set.of("id","managementNo","name","companyId","companyName","employeeType","grade","job","status","useYn","currentProjects","withdrawalDate","monthlyMm","revision","gradeCode","jobCode","subJobCode","positionCode","directClass","partId","hireDate","homeAreaCode","homeAddress","skills","priceHistory","editPrice"));
        Set<String> assignmentFields=new HashSet<>(Set.of("id","projectId","projectName","developerId","developerName","managementNo","jobCode","jobName","startDate","endDate","contractMm","inputType","contractDeveloperId","extensionPossibility","extensionCount","earlyWithdrawal","assignmentStatus","yearMm","revision","warnings","protectedMonths","month","monthlyMm","inputDays","fixed"));
        if(candidateRate(roles)) {developerFields.add("monthlyRate");assignmentFields.addAll(Set.of("billingUnitPrice","appliedUnitPrice","defaultUnitPrice","salesAmount"));}
        if(sensitive)developerFields.add("monthlyPay");
        return new Capabilities(Collections.unmodifiableMap(screens),scope,sensitive,Map.of("UI-33",Set.copyOf(candidateFields),"UI-32",Set.copyOf(renegotiationFields),"UI-02",Set.copyOf(developerFields),"UI-12",Set.copyOf(assignmentFields)),Map.of("automationLogs",all && administrator,"analysisPanel",screens.getOrDefault("UI-26",Set.of()).contains("INQ"),"userAccessLogs",administrator && screens.containsKey("UI-23"),"roleStop",administrator && screens.getOrDefault("UI-24",Set.of()).contains("UPD"),"managePartners",roles.stream().anyMatch(role->Set.of("ADMIN","FIN").contains(role.roleId()))),Set.copyOf(pcOnlyScreens));
    }
    private int compareMenus(PermissionMapper.Menu left,PermissionMapper.Menu right,Map<String,PermissionMapper.Menu> rows) {
        var a=menuPath(left,rows);var b=menuPath(right,rows);
        for(int i=0;i<Math.min(a.size(),b.size());i++) {
            int order=Integer.compare(a.get(i).sortOrder(),b.get(i).sortOrder());if(order!=0)return order;
            order=a.get(i).menuId().compareTo(b.get(i).menuId());if(order!=0)return order;
        }
        return Integer.compare(a.size(),b.size());
    }
    private List<PermissionMapper.Menu> menuPath(PermissionMapper.Menu row,Map<String,PermissionMapper.Menu> rows) {
        List<PermissionMapper.Menu> path=new ArrayList<>();Set<String> visited=new HashSet<>();
        while(row!=null && visited.add(row.menuId())) {path.add(row);row=rows.get(row.parentId());}
        Collections.reverse(path);return path;
    }
    private boolean valid(PermissionMapper.Menu row,Map<String,PermissionMapper.Menu> rows) {
        Set<String> visited=new HashSet<>();
        while(row!=null) {
            if(!visited.add(row.menuId()) || !"Y".equals(row.useYn()))return false;
            if(row.parentId()==null)return true;
            row=rows.get(row.parentId());
        }
        return false;
    }
    public boolean allows(String screen,String action) {return capabilities().screens().getOrDefault(screen,Set.of()).contains(action);}
    public boolean activeUser(String id) {return latest(()->mapper.active(id));}
    public void require(String screen,String action) {if(!allows(screen,action))throw denied();}
    public void requireAll() {if(!capabilities().scope().all())throw denied();}
    public void requireSensitive() {if(!capabilities().sensitive())throw denied();}
    public boolean hasRole(String role) {return latest(()->mapper.roles(userId()).stream().anyMatch(row->role.equals(row.roleId())));}
    public Map<String,Set<String>> menuActions() {
        var capabilities=capabilities();var rows=mapper.menus(userId());Map<String,PermissionMapper.Menu> indexed=new HashMap<>();rows.forEach(row->indexed.put(row.menuId(),row));
        Map<String,Set<String>> result=new HashMap<>();
        for(var row:rows)if(valid(row,indexed) && row.inquiry())result.put(row.menuId(),capabilities.screens().getOrDefault(row.screenId()==null?"":row.screenId(),Set.of()));
        return Map.copyOf(result);
    }
    public boolean candidateRateAllowed() {return capabilities().fields().get("UI-33").contains("desiredRate");}
    private boolean candidateRate(List<PermissionMapper.Role> roles) {return roles.stream().anyMatch(role->!"PROJ".equals(role.scope()) || "Y".equals(role.sensitive()));}
    public void requireRole(String... roles) {if(Arrays.stream(roles).noneMatch(this::hasRole))throw denied();}
    public boolean projectAllowed(String id,String month) {
        if(id==null || month==null)return false;
        var scope=capabilities().scope();
        if(scope.all() || scope.projectIds().contains(id))return true;
        String part=latest(()->mapper.projectPart(id,month));
        return part!=null && scope.partIds().contains(part);
    }
    public void requireProject(String id,String month) {if(!projectAllowed(id,month))throw new ApiException(404,"NOT_FOUND","요청한 자료를 찾을 수 없습니다.");}
    public void requireCurrentProject(String id) {requireProject(id,YearMonth.now(clock.withZone(TimeConfiguration.SEOUL)).toString().replace("-",""));}
    public boolean currentProjectAllowed(String id) {return projectAllowed(id,YearMonth.now(clock.withZone(TimeConfiguration.SEOUL)).toString().replace("-",""));}
    public boolean assignmentQueryAllowed() {return allows("UI-12","INQ");}
    public boolean assignmentPersonAllowed(String id) {String month=YearMonth.now(clock.withZone(TimeConfiguration.SEOUL)).toString().replace("-","");return mapper.developerProjects(id).stream().anyMatch(project->projectAllowed(project,month));}
    public void requireBill(String id) {var bill=mapper.bill(id);if(bill==null)throw new ApiException(404,"NOT_FOUND","요청한 자료를 찾을 수 없습니다.");requireProject(bill.projectId(),bill.month());}
    public void requireClientWrite(String id,String type) {
        String existing=id==null?null:mapper.clientType(id);
        if(id!=null && existing==null)throw new ApiException(404,"NOT_FOUND","요청한 자료를 찾을 수 없습니다.");
        if("VNDR".equals(type) || "BOTH".equals(type) || "VNDR".equals(existing) || "BOTH".equals(existing))requireRole("ADMIN","FIN");
        else requireRole("ADMIN","FIN","SALES_LDR");
    }
    public void requireProjectPlanner(String id) {requireCurrentProject(id);if(!hasRole("ADMIN") && !(hasRole("PM") && mapper.projects(userId()).contains(id)))throw denied();}
    public String requireUpload(String id,String action) {
        if(id==null || !id.matches("[1-9][0-9]{0,11}"))throw com.sicost.common.validation.InputValidator.invalid("uploadId");
        var upload=mapper.upload(id);
        if(upload==null || !userId().equals(upload.ownerId()))throw new ApiException(404,"NOT_FOUND","요청한 자료를 찾을 수 없습니다.");
        String screen=switch(upload.formType()) {case "B1","B8"->"UI-02";case "B2"->"UI-10";case "B5"->"UI-12";case "B6","B9"->"UI-01";case "B7"->"UI-15";case "B10"->"UI-18";case "B11"->"UI-08";default->throw denied();};
        require(screen,action);requireRole("ADMIN","FIN");
        if(java.util.Set.of("B1","B2","B10","B11").contains(upload.formType())) {requireAll();requireSensitive();}
        if(upload.formType().equals("B8"))requireSensitive();
        if(upload.projectId()!=null)requireProject(upload.projectId(),upload.month()==null?YearMonth.now(clock.withZone(TimeConfiguration.SEOUL)).toString().replace("-",""):upload.month());
        return screen;
    }
    public static ApiException denied() {return new ApiException(403,"FORBIDDEN","이 작업을 수행할 권한이 없습니다.");}
}
