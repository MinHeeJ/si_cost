package com.sicost.businessusers;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RevisionService;
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
        String actor=users.userId();validateRoles(input.businessRoleIds());
        LocalDateTime now=LocalDateTime.now(clock);
        byte[] bytes=new byte[32];random.nextBytes(bytes);
        String hash;
        try {hash=encoder.encode(Base64.getEncoder().encodeToString(bytes));} finally {Arrays.fill(bytes,(byte)0);}
        if (repository.insert(new NewUser(input,hash,actor,now))!=1)
            throw new ApiException(409,"DUPLICATE_USER","이미 등록된 아이디입니다.","userId",null);
        input.businessRoleIds().forEach(role->requireWrite(repository.link(new RoleLink(input.userId(),role,actor,now))));
        append(input.userId(),"NEW","등록",null,input.userId()+" "+input.name());
        access.append(new AccessLogWriter.Event("USER_REG","UI-23",input.userId(),null,null),request);
        Stored row=required(input.userId(),false);
        return new Result(true,"등록했습니다.",view(row,repository.roles(input.userId())));
    }
    @Transactional
    public Result update(String id,Edit input) {
        String actor=users.userId();Stored row=required(id,true);List<Role> roles=repository.roles(id);
        revisions.requireExpected(input.expectedRevision(),revision(row,roles));
        validateRoles(input.businessRoleIds());
        List<String> before=businessIds(roles);
        if (roles.stream().filter(role->!BusinessUserInput.BUSINESS_ROLES.contains(role.roleId())).count()+input.businessRoleIds().size()==0)
            throw new ApiException(400,"VALIDATION_ERROR","사용자는 하나 이상의 역할을 가져야 합니다.","businessRoleIds",null);
        if (before.contains("SALES_LDR") && !input.businessRoleIds().contains("SALES_LDR")) {
            List<String> parts=repository.leaderParts(id,YearMonth.now(clock).toString().replace("-",""));
            if (!parts.isEmpty()) throw new ApiException(409,"USER_IS_PART_LEADER",parts.get(0)+": 영업 파트에서 먼저 파트장을 바꾸세요.","businessRoleIds",Map.of("partName",parts.get(0)));
        }
        boolean roleChanged=!before.equals(input.businessRoleIds());
        if (Objects.equals(row.name(),input.name()) && Objects.equals(row.email(),input.email()) && !roleChanged)
            return new Result(false,"바뀐 내용이 없습니다.",view(row,roles));
        LocalDateTime now=LocalDateTime.now(clock);
        requireWrite(repository.update(new Mutation(id,input,actor,now)));
        if (roleChanged) {
            before.stream().filter(role->!input.businessRoleIds().contains(role)).forEach(role->requireWrite(repository.unlink(new RoleLink(id,role,actor,now))));
            input.businessRoleIds().stream().filter(role->!before.contains(role)).forEach(role->requireWrite(repository.link(new RoleLink(id,role,actor,now))));
            append(id,"UPD","업무 역할",String.join(",",before),String.join(",",input.businessRoleIds()));
        }
        if (!Objects.equals(row.name(),input.name())) append(id,"UPD","이름",row.name(),input.name());
        if (!Objects.equals(row.email(),input.email())) append(id,"UPD","이메일",row.email(),input.email());
        return new Result(true,"저장했습니다.",view(required(id,false),repository.roles(id)));
    }
    private Stored required(String id,boolean lock) {
        InputValidator.text(id,30,"id");Stored row=repository.find(id,lock);
        if (row==null) throw new ApiException(404,"NOT_FOUND","사용자를 찾을 수 없습니다.");return row;
    }
    private void validateRoles(List<String> requested) {
        Set<String> available=new HashSet<>();repository.availableRoles().forEach(role->available.add(role.roleId()));
        if (!available.containsAll(requested)) throw new ApiException(400,"VALIDATION_ERROR","사용 중인 업무 역할을 선택해 주세요.","businessRoleIds",null);
    }
    private void requireWrite(int count) {if(count!=1)throw new IllegalStateException("사용자 정보를 저장하지 못했습니다.");}
    private void append(String id,String type,String item,String before,String after) {
        history.append(new ChangeHistoryWriter.Change("TB_SY_USER",id,type,item,before,after,null));
    }
    private List<String> businessIds(List<Role> roles) {return roles.stream().map(Role::roleId).filter(BusinessUserInput.BUSINESS_ROLES::contains).sorted().toList();}
    private String revision(Stored row,List<Role> roles) {
        return revisions.revision(new Snapshot(row.userId(),row.name(),row.email(),row.regDttm(),row.modDttm(),roles.stream().map(Role::roleId).sorted().toList()));
    }
    private User view(Stored row,List<Role> roles) {
        return new User(row.userId(),row.name(),row.email(),roles.stream().sorted(Comparator.comparing(Role::roleId)).map(Role::name).toList(),
            businessIds(roles),row.status(),row.lastLoginAt()==null?null:row.lastLoginAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),revision(row,roles));
    }
}
