package com.sicost.roles;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.*;
import com.sicost.common.validation.InputValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.roles.RoleDtos.*;

@Service
public class RoleService {
    private static final List<String> KEYS=List.of(SettlementSourceLocks.ROLE_RESOURCE,SettlementSourceLocks.MENU_RESOURCE);
    private final RoleRepository repository;
    private final WriteCoordinator writes;
    private final RevisionService revisions;
    private final CurrentUserProvider users;
    private final ChangeHistoryWriter history;
    private final AccessLogWriter access;
    private Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public void setClock(Clock clock) {this.clock=clock;}
    public RoleService(RoleRepository repository,WriteCoordinator writes,RevisionService revisions,CurrentUserProvider users,
                       ChangeHistoryWriter history,AccessLogWriter access) {
        this.repository=repository;this.writes=writes;this.revisions=revisions;this.users=users;this.history=history;this.access=access;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ManagedRolesData list() {
        users.currentUser();
        var items=repository.roles().stream().filter(r->"Y".equals(r.useYn())).map(r->new ManagedRole(r.roleId(),r.name(),r.dataScope(),
            yes(r.sensitiveYn()),yes(r.systemYn()),true,r.sortOrder(),revision(r.roleId()))).toList();
        return new ManagedRolesData(items,true,false,settingsRevision());
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ManagedRoleDetail get(String requested) {users.currentUser();return detail(RoleInput.id(requested));}
    @Transactional
    public ManagedRoleSaveResult create(Create input,HttpServletRequest request) {
        String actor=users.userId();String[] created=new String[1];
        writes.create(KEYS,()->repository.snapshot(created[0]),()->{},()->{
            List<Stored> all=repository.roles();
            if(all.stream().anyMatch(r->r.name().equals(input.name())))
                throw new ApiException(409,"DUPLICATE_NAME","이미 등록된 역할 이름입니다.","name",null);
            Stored source=null;
            if(input.mode().equals("COPY")) {
                source=repository.required(input.sourceRoleId());
                if(!yes(source.useYn()))throw new ApiException(409,"INVALID_STATE","사용 중인 원본 역할을 선택해 주세요.","sourceRoleId",null);
                revisions.requireExpected(input.sourceRevision(),revision(source.roleId()));
            }
            int sort=all.stream().mapToInt(Stored::sortOrder).max().orElse(0);
            if(sort>=99999)throw new ApiException(409,"SORT_CAPACITY_EXCEEDED","역할 표시 순서의 최대 용량을 초과했습니다.");
            BigInteger maximum=all.stream().map(Stored::roleId).filter(id->id.matches("R-[0-9]+"))
                .map(id->new BigInteger(id.substring(2))).max(BigInteger::compareTo).orElse(BigInteger.ZERO);
            String digits=maximum.add(BigInteger.ONE).toString();String id="R-"+"0".repeat(Math.max(0,3-digits.length()))+digits;
            if(id.length()>20)throw new ApiException(409,"SORT_CAPACITY_EXCEEDED","역할 식별자 용량을 초과했습니다.");
            created[0]=id;
            LocalDateTime now=now();
            Stored row=new Stored(id,input.name(),source==null?input.dataScope():source.dataScope(),source==null?"N":source.sensitiveYn(),"N","Y",sort+1);
            repository.insert(new RoleWrite(id,row,actor,now));
            auditRole(id,"NEW","ROLE_NM",null,row.name());auditRole(id,"NEW","DATA_SCOPE_CD",null,row.dataScope());
            auditRole(id,"NEW","SNSTV_VIEW_YN",null,row.sensitiveYn());auditRole(id,"NEW","SYS_ROLE_YN",null,"N");
            auditRole(id,"NEW","USE_YN",null,"Y");auditRole(id,"NEW","SORT_ORD",null,Integer.toString(row.sortOrder()));
            for(Permission p:repository.permissions(source==null?id:source.roleId())) {
                PermissionInput value=source==null?new PermissionInput(p.menuId(),false,false,false,false,false,false,false):p.input();
                repository.permission(new PermissionWrite(id,value,actor,now));auditPermission(id,"NEW",null,value);
            }
            access.append(new AccessLogWriter.Event("AUTH_CHG","UI-24",id,null,null),request);
        });
        return result(detail(created[0]),true);
    }
    @Transactional
    public ManagedRoleSaveResult update(String requested,Edit input,HttpServletRequest request) {
        String id=RoleInput.id(requested),actor=users.userId();
        List<PermissionInput> before=new ArrayList<>();
        var outcome=writes.write(KEYS,input.expectedRevision(),()->repository.snapshot(id),()->{
            Stored row=repository.required(id);
            if(!yes(row.useYn()))throw new ApiException(409,"INVALID_STATE","사용 중지된 역할은 편집할 수 없습니다.");
            List<Permission> permissions=repository.permissions(id);
            Set<String> expected=new HashSet<>();permissions.forEach(p->expected.add(p.menuId()));
            Set<String> actual=new HashSet<>();input.menuPermissions().forEach(p->actual.add(p.menuId()));
            if(actual.size()!=input.menuPermissions().size() || !expected.equals(actual))throw InputValidator.invalid("menuPermissions");
            if(id.equals("ADMIN")) {
                Set<String> protectedIds=new HashSet<>();permissions.stream().filter(p->"UI-24".equals(p.screenId())).forEach(p->protectedIds.add(p.menuId()));
                if(input.menuPermissions().stream().anyMatch(p->protectedIds.contains(p.menuId()) && (!p.inquire() || !p.update())))
                    throw new ApiException(409,"ADMIN_PERMISSION_PROTECTED","관리자의 권한 화면 조회·수정 권한은 유지해야 합니다.","menuPermissions",null);
            }
            permissions.forEach(p->before.add(p.input()));
        },()->{
            Stored row=repository.required(id);
            return !row.dataScope().equals(input.dataScope()) || yes(row.sensitiveYn())!=input.sensitiveView()
                || !new HashSet<>(before).equals(new HashSet<>(input.menuPermissions()));
        },()->{
            Stored row=repository.required(id);LocalDateTime now=now();
            Stored updated=new Stored(id,row.name(),input.dataScope(),input.sensitiveView()?"Y":"N",row.systemYn(),row.useYn(),row.sortOrder());
            repository.update(new RoleWrite(id,updated,actor,now));
            if(!row.dataScope().equals(updated.dataScope()))auditRole(id,"UPD","DATA_SCOPE_CD",row.dataScope(),updated.dataScope());
            if(!row.sensitiveYn().equals(updated.sensitiveYn()))auditRole(id,"UPD","SNSTV_VIEW_YN",row.sensitiveYn(),updated.sensitiveYn());
            Map<String,PermissionInput> old=new HashMap<>();before.forEach(p->old.put(p.menuId(),p));
            for(PermissionInput p:input.menuPermissions())if(!p.equals(old.get(p.menuId()))) {
                PermissionWrite write=new PermissionWrite(id,p,actor,now);
                boolean exists=repository.permissionExists(write);
                repository.permission(write);
                auditPermission(id,exists?"UPD":"NEW",exists?old.get(p.menuId()):null,p);
            }
            access.append(new AccessLogWriter.Event("AUTH_CHG","UI-24",id,null,null),request);
        });
        return result(detail(id),outcome.changed());
    }
    private ManagedRoleSaveResult result(ManagedRoleDetail detail,boolean changed) {return new ManagedRoleSaveResult(detail,detail.revision(),changed);}
    private ManagedRoleDetail detail(String id) {
        Stored row=repository.required(id);
        return new ManagedRoleDetail(id,row.name(),row.dataScope(),yes(row.sensitiveYn()),yes(row.systemYn()),yes(row.useYn()),row.sortOrder(),
            revision(id),repository.permissions(id),true,false,settingsRevision());
    }
    private String revision(String id) {return revisions.revision(repository.snapshot(id));}
    private String settingsRevision() {return revisions.revision(repository.settingsSnapshot());}
    private boolean yes(String value) {return "Y".equals(value);}
    private LocalDateTime now() {return LocalDateTime.ofInstant(clock.instant(),com.sicost.common.TimeConfiguration.SEOUL);}
    private void auditRole(String id,String type,String item,String before,String after) {
        history.append(new ChangeHistoryWriter.Change("TB_SY_ROLE",id,type,item,before,after,null));
    }
    private void auditPermission(String id,String type,PermissionInput before,PermissionInput after) {
        history.append(new ChangeHistoryWriter.Change("TB_SY_ROLE_MENU_AUTH",id+":"+after.menuId(),type,"메뉴 권한",
            before==null?null:permissionJson(before),permissionJson(after),null));
    }
    static String permissionJson(PermissionInput p) {
        return "{\"INQ_YN\":\""+yn(p.inquire())+"\",\"REG_YN\":\""+yn(p.register())+"\",\"UPD_YN\":\""+yn(p.update())
            +"\",\"DEL_YN\":\""+yn(p.delete())+"\",\"UPLD_YN\":\""+yn(p.upload())+"\",\"DNLD_YN\":\""+yn(p.download())+"\",\"CNFM_YN\":\""+yn(p.confirm())+"\"}";
    }
    private static String yn(boolean value) {return value?"Y":"N";}
}
