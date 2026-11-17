package com.sicost.businessusers;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.concurrent.SettlementSourceLocks;
import com.sicost.common.concurrent.MonthCloseBoundary;
import com.sicost.common.concurrent.CommitInvalidation;
import com.sicost.pm.ProjectPmRepository;
import com.sicost.pm.ProjectPmDtos;
import com.sicost.common.validation.ProjectInputPolicy;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.AccessLogWriter;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.validation.InputValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.businessusers.BusinessUserDtos.*;

@Service
public class BusinessUserService {
    private final BusinessUserRepository repository;
    private final RevisionService revisions;
    private final CurrentUserProvider users;
    private final ChangeHistoryWriter history;
    private final AccessLogWriter access;
    private final Clock clock;
    private SettlementSourceLocks sourceLocks;
    private MonthCloseBoundary closeBoundary;
    private CommitInvalidation invalidation;
    private ProjectPmRepository projects;
    private com.sicost.authentication.AuthenticationMapper authentication;
    @org.springframework.beans.factory.annotation.Autowired
    public void setAuthentication(com.sicost.authentication.AuthenticationMapper authentication) {this.authentication=authentication;}
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setWriteInfrastructure(SettlementSourceLocks locks,MonthCloseBoundary boundary,CommitInvalidation invalidation) {
        this.sourceLocks=locks;this.closeBoundary=boundary;this.invalidation=invalidation;
    }
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setProjects(ProjectPmRepository projects) {this.projects=projects;}
    private final SecureRandom random=new SecureRandom();
    private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(10);
    public BusinessUserService(BusinessUserRepository repository,RevisionService revisions,CurrentUserProvider users,
                               ChangeHistoryWriter history,AccessLogWriter access,Clock clock) {
        this.repository=repository;this.revisions=revisions;this.users=users;this.history=history;this.access=access;this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page list(Filter filter) {
        users.currentUser();
        long total=repository.count(filter);
        return new Page(repository.list(filter).stream().map(row->view(row,repository.roles(row.userId()))).toList(),total,filter.page(),filter.size());
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public User get(String id) {
        users.currentUser();Stored row=required(id,false);return view(row,repository.roles(id));
    }
    @Transactional
    public Result create(Create input,HttpServletRequest request) {
        String actor=users.userId();
        Set<String> keys=new TreeSet<>();keys.add("USER:"+input.userId());
        Map<String,List<ProjectPmDtos.Scope>> oldProjects=new LinkedHashMap<>();
        if(!input.projectAssignments().isEmpty() && projects==null)throw new IllegalStateException("프로젝트 서비스가 구성되지 않았습니다.");
        for(var assignment:input.projectAssignments()) {
            projects.required(assignment.projectId());
            var old=projects.scopes(assignment.projectId());oldProjects.put(assignment.projectId(),old);
            keys.add("PROJECT:"+assignment.projectId());old.forEach(scope->keys.add("USER:"+scope.userId()));
        }
        lock(keys,input.userId());
        validateRoles(input.requestedRoles(),input.roleIds()==null?"businessRoleIds":"roleIds");
        if(!input.projectAssignments().isEmpty() && !input.requestedRoles().contains("PM"))
            throw new ApiException(409,"PM_QUALIFICATION_REQUIRED","담당 프로젝트는 PM 역할이 필요합니다.");
        for(var assignment:input.projectAssignments()) {
            var project=projects.required(assignment.projectId());
            revisions.requireExpected(assignment.expectedRevision(),revisions.revision(projects.snapshot(assignment.projectId())));
            if(!Set.of("ING","PRE").contains(project.status()) || project.endDate().isBefore(ProjectInputPolicy.today(clock)))
                throw new ApiException(409,"INVALID_PROJECT_STATUS","진행 또는 수주예정 프로젝트만 지정할 수 있습니다.");
            var old=projects.scopes(assignment.projectId());
            if(!old.equals(oldProjects.get(assignment.projectId())))throw new ApiException(409,"STALE_WRITE","담당 연결이 변경되었습니다. 다시 조회해 주세요.");
            if(!old.isEmpty() && !assignment.replaceConfirmed())
                throw new ApiException(409,"PM_REPLACEMENT_REQUIRED","기존 PM 전체를 확인한 뒤 교체해 주세요.","replaceConfirmed",Map.of("projectId",assignment.projectId(),"currentPms",old));
        }
        LocalDateTime now=now();
        byte[] bytes=new byte[32];random.nextBytes(bytes);
        String hash;
        try {hash=encoder.encode(Base64.getEncoder().encodeToString(bytes));} finally {Arrays.fill(bytes,(byte)0);}
        if (repository.insert(new NewUser(input,hash,actor,now))!=1)
            throw new ApiException(409,"DUPLICATE_USER","이미 등록된 아이디입니다.","userId",null);
        input.requestedRoles().forEach(role->{
            requireWrite(repository.link(new RoleLink(input.userId(),role,actor,now)));
            roleAudit(input.userId(),role,"NEW");
        });
        if(authentication!=null)authentication.synchronizeMfa(input.userId());
        if(input.headOffice())headOffice(input.userId(),true,actor,now);
        for(var assignment:input.projectAssignments()) {
            String projectId=assignment.projectId();var old=oldProjects.get(projectId);
            for(var scope:old) {
                projects.delete(new ProjectPmDtos.ScopeWrite(scope.userId(),scope.scopeSn(),projectId,actor,now));
                scopeAudit(scope.userId(),scope.scopeSn(),"PROJ:"+projectId+";PART:NULL","DEL");
            }
            int serial=ProjectInputPolicy.nextSerial(repository.greatestScopeSn(input.userId()));
            projects.insert(new ProjectPmDtos.ScopeWrite(input.userId(),serial,projectId,actor,now));
            scopeAudit(input.userId(),serial,"PROJ:"+projectId+";PART:NULL","NEW");
            projects.history(new ProjectPmDtos.PmHistory(projectId,ProjectInputPolicy.nextSerial(projects.greatestHistorySn(projectId)),
                old.isEmpty()?null:pmSummary(old),input.name()+" ("+input.userId()+")",actor,now));
        }
        invalidate(keys);
        append(input.userId(),"NEW","등록",null,input.userId()+" "+input.name());
        access.append(new AccessLogWriter.Event("USER_REG","UI-23",input.userId(),null,null),request);
        Stored row=required(input.userId(),false);
        return new Result(true,"사용자를 등록했습니다.",view(row,repository.roles(input.userId())));
    }
    @Transactional
    public Result update(String id,Edit input) {
        String actor=users.userId();InputValidator.text(id,30,"id");lock(List.of("USER:"+id),id);
        Stored row=required(id,true);List<Role> roles=repository.roles(id);
        revisions.requireExpected(input.expectedRevision(),revision(row,roles));
        String roleField=input.roleIds()==null?"businessRoleIds":"roleIds";
        List<String> requested=input.roleIds()==null?input.businessRoleIds():input.roleIds();
        List<String> allBefore=roles.stream().map(Role::roleId).sorted().toList();
        List<String> before=input.roleIds()==null?businessIds(roles):allBefore;
        // Retained historical inactive roles are allowed; new connections must be active.
        validateRoles(requested.stream().filter(role->!allBefore.contains(role)).toList(),roleField);
        List<String> after=new ArrayList<>(requested);
        if(input.roleIds()==null)allBefore.stream().filter(role->!BusinessUserInput.BUSINESS_ROLES.contains(role)).forEach(after::add);
        Collections.sort(after);
        if(after.isEmpty())throw new ApiException(400,"VALIDATION_ERROR","사용자는 하나 이상의 역할을 가져야 합니다.",roleField,null);
        if("USE".equals(row.status()) && allBefore.contains("ADMIN") && !after.contains("ADMIN") &&
            repository.availableRoles().stream().anyMatch(role->role.roleId().equals("ADMIN")) && repository.activeAdministratorCount()<=1)
            throw new ApiException(409,"LAST_ACTIVE_ADMIN","마지막 사용 가능한 관리자의 ADMIN 역할은 제거할 수 없습니다.");
        if (before.contains("PM") && !requested.contains("PM")) {
            List<String> projects=repository.connectedProjects(id);
            if (!projects.isEmpty()) throw new ApiException(409,"USER_IS_PROJECT_PM","담당 프로젝트를 먼저 해제한 뒤 PM 역할을 제거해 주세요.","businessRoleIds",Map.of("projectIds",projects));
        }
        if (before.contains("SALES_LDR") && !requested.contains("SALES_LDR")) {
            List<String> parts=repository.leaderParts(id,YearMonth.from(now()).toString().replace("-",""));
            if (!parts.isEmpty()) throw new ApiException(409,"USER_IS_PART_LEADER",String.join(", ",parts)+": 영업 파트에서 먼저 파트장을 바꾸세요.","businessRoleIds",Map.of("partName",parts.get(0),"partNames",parts));
        }
        boolean roleChanged=!allBefore.equals(after);
        boolean officeChanged=input.headOffice()!=null && input.headOffice()!=repository.headOffice(id);
        if (Objects.equals(row.name(),input.name()) && Objects.equals(row.email(),input.email()) && !roleChanged && !officeChanged)
            return new Result(false,"바뀐 내용이 없습니다.",view(row,roles));
        LocalDateTime now=now();
        requireWrite(repository.update(new Mutation(id,input,actor,now)));
        if (roleChanged) {
            allBefore.stream().filter(role->!after.contains(role)).forEach(role->{
                requireWrite(repository.unlink(new RoleLink(id,role,actor,now)));roleAudit(id,role,"DEL");
            });
            after.stream().filter(role->!allBefore.contains(role)).forEach(role->{
                requireWrite(repository.link(new RoleLink(id,role,actor,now)));roleAudit(id,role,"NEW");
            });
        }
        if(officeChanged)headOffice(id,input.headOffice(),actor,now);
        if(roleChanged && authentication!=null)authentication.synchronizeMfa(id);
        if(roleChanged || officeChanged)access.append(new AccessLogWriter.Event("AUTH_CHG","UI-23",id,null,null),request());
        invalidate(List.of("USER:"+id));
        if (!Objects.equals(row.name(),input.name())) append(id,"UPD","이름",row.name(),input.name());
        if (!Objects.equals(row.email(),input.email())) append(id,"UPD","이메일",row.email(),input.email());
        return new Result(true,"저장했습니다.",view(required(id,false),repository.roles(id)));
    }
    private Stored required(String id,boolean lock) {
        InputValidator.text(id,30,"id");Stored row=repository.find(id,lock);
        if (row==null) throw new ApiException(404,"NOT_FOUND","사용자를 찾을 수 없습니다.");return row;
    }
    private void validateRoles(List<String> requested,String field) {
        Set<String> available=new HashSet<>();repository.availableRoles().forEach(role->available.add(role.roleId()));
        for(String id:requested)if(!available.contains(id)) {
            Role role=repository.findRole(id);
            if(role!=null && !"Y".equals(role.useYn()))throw new ApiException(409,"ROLE_INACTIVE","사용 중지된 역할은 새로 연결할 수 없습니다.",field,null);
        }
        if (!available.containsAll(requested)) throw new ApiException(400,"VALIDATION_ERROR","사용 중인 역할을 선택해 주세요.",field,null);
    }
    private LocalDateTime now() {return LocalDateTime.ofInstant(clock.instant(),ZoneId.of("Asia/Seoul"));}
    private void lock(Collection<String> keys,String id) {
        if(sourceLocks!=null) {closeBoundary.shared();sourceLocks.lock(keys);}
        else repository.lockUser(id);
    }
    private void invalidate(Collection<String> keys) {if(invalidation!=null)keys.forEach(invalidation::afterCommit);}
    private HttpServletRequest request() {
        var attrs=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if(attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servlet)return servlet.getRequest();
        return null;
    }
    private void roleAudit(String id,String role,String type) {
        history.append(new ChangeHistoryWriter.Change("TB_SY_USER_ROLE",id+":"+role,type,"ROLE_ID",type.equals("DEL")?role:null,type.equals("NEW")?role:null,null));
    }
    private void scopeAudit(String id,int serial,String value,String type) {
        history.append(new ChangeHistoryWriter.Change("TB_SY_USER_SCOPE",id+":"+serial,type,"담당",type.equals("DEL")?value:null,type.equals("NEW")?value:null,null));
    }
    private void headOffice(String id,boolean selected,String actor,LocalDateTime now) {
        Integer old=repository.headOfficeScopeSn(id);
        if(selected && old==null) {
            int serial=ProjectInputPolicy.nextSerial(repository.greatestScopeSn(id));
            requireWrite(repository.insertHeadOffice(new HeadOfficeWrite(id,serial,actor,now)));scopeAudit(id,serial,"PART:HQ;PROJ:NULL","NEW");
        } else if(!selected && old!=null) {
            requireWrite(repository.deleteHeadOffice(new HeadOfficeWrite(id,old,actor,now)));scopeAudit(id,old,"PART:HQ;PROJ:NULL","DEL");
        }
    }
    private String pmSummary(List<ProjectPmDtos.Scope> scopes) {
        String value=String.join(", ",scopes.stream().map(scope->scope.name()+" ("+scope.userId()+")").toList());
        return value.length()<=500?value:"PM "+scopes.size()+"명; 연결 revision="+revisions.revision(scopes);
    }
    private void requireWrite(int count) {if(count!=1)throw new IllegalStateException("사용자 정보를 저장하지 못했습니다.");}
    private void append(String id,String type,String item,String before,String after) {
        history.append(new ChangeHistoryWriter.Change("TB_SY_USER",id,type,item,before,after,null));
    }
    private List<String> businessIds(List<Role> roles) {return roles.stream().map(Role::roleId).filter(BusinessUserInput.BUSINESS_ROLES::contains).sorted().toList();}
    private String revision(Stored row,List<Role> roles) {
        return revisions.revision(repository.snapshot(row.userId()));
    }
    private User view(Stored row,List<Role> roles) {
        return new User(row.userId(),row.name(),row.email(),roles.stream().sorted(Comparator.comparing(Role::roleId)).map(Role::name).toList(),
            businessIds(roles),row.status(),row.lastLoginAt()==null?null:row.lastLoginAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),revision(row,roles),
            roles.stream().map(Role::roleId).sorted().toList(),repository.headOffice(row.userId()),
            repository.assignedParts(row.userId(),YearMonth.from(LocalDateTime.ofInstant(clock.instant(),ZoneId.of("Asia/Seoul"))).toString().replace("-","")),repository.tempPassword(row.userId()));
    }
}
