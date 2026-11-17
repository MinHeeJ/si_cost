package com.sicost.menumanagementops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.SettlementSourceLocks;
import com.sicost.common.concurrent.WriteCoordinator;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.validation.InputValidator;
import com.sicost.menumanagement.MenuManagementDtos.*;
import com.sicost.menumanagement.MenuManagementService;
import java.math.BigInteger;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.menumanagementops.MenuRegistrationDtos.*;

@Service
public class MenuRegistrationService {
    private final MenuRegistrationRepository repository;
    private final WriteCoordinator writes;
    private final CurrentUserProvider users;
    private final ChangeHistoryWriter history;
    private final Clock clock;
    private final MenuManagementService menus;
    public MenuRegistrationService(MenuRegistrationRepository repository, WriteCoordinator writes, CurrentUserProvider users,
                                   ChangeHistoryWriter history, Clock clock, MenuManagementService menus) {
        this.repository=repository; this.writes=writes; this.users=users; this.history=history; this.clock=clock; this.menus=menus;
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public AvailableScreens getMenuManagementScreens() {
        users.currentUser();
        List<Stored> tree=repository.tree();
        return new AvailableScreens(available(tree),repository.revision(tree));
    }
    private List<AvailableScreen> available(List<Stored> tree) {
        Set<String> linked=new HashSet<>(); tree.forEach(row -> linked.add(row.screenId()));
        return repository.screens().stream().filter(screen -> screen.implemented()
            && !Set.of("UI-22","UI-20").contains(screen.screenId()) && !linked.contains(screen.screenId()))
            .sorted(Comparator.comparing(com.sicost.shared.MenuService.ScreenRegistration::screenId))
            .map(screen -> new AvailableScreen(screen.screenId(),screen.name())).toList();
    }
    @Transactional
    public SaveResult createMenu(Create input) {
        String actor=users.userId();
        List<Stored> before=new ArrayList<>();
        var result=writes.write(List.of(SettlementSourceLocks.ROLE_RESOURCE,SettlementSourceLocks.MENU_RESOURCE),
            input.expectedRevision(),repository::tree,() -> {
                before.addAll(repository.tree());
                validate(input,before);
            },() -> true,() -> {
                String id=nextId(input.level()==1?"G":"M",before);
                int order=before.stream().filter(row -> Objects.equals(row.parentId(),input.parentId()))
                    .mapToInt(Stored::sortOrder).max().orElse(0)+1;
                if(order>99999) throw new ApiException(409,"SORT_CAPACITY_EXCEEDED","메뉴 순서의 최대값을 초과했습니다.","parentId",null);
                LocalDateTime now=LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);
                repository.insertMenu(new NewMenu(id,input,order,actor,now));
                menuHistory(id,input,order);
                if(input.level()==2) for(String role:repository.roleIds()) {
                    repository.insertPermission(new NewPermission(role,id,actor,now));
                    history.append(new ChangeHistoryWriter.Change("TB_SY_ROLE_MENU_AUTH",role+":"+id,"NEW","메뉴권한",null,
                        "{\"INQ_YN\":\"N\",\"REG_YN\":\"N\",\"UPD_YN\":\"N\",\"DEL_YN\":\"N\",\"UPLD_YN\":\"N\",\"DNLD_YN\":\"N\",\"CNFM_YN\":\"N\"}",null));
                }
            });
        ManagedMenuTree value=menus.getMenuManagement();
        return new SaveResult(value,result.revision(),result.changed());
    }
    private void validate(Create input,List<Stored> rows) {
        if(input.level()==2) {
            Stored parent=rows.stream().filter(row -> row.menuId().equals(input.parentId())).findFirst()
                .orElseThrow(() -> new ApiException(404,"NOT_FOUND","상위 메뉴를 찾을 수 없습니다.","parentId",null));
            if(parent.level()!=1) throw InputValidator.invalid("parentId");
            if(rows.stream().anyMatch(row -> Objects.equals(row.screenId(),input.screenId())))
                throw new ApiException(409,"DUPLICATE_SCREEN","이미 연결된 화면입니다. 기존 메뉴에서 재개해 주세요.","screenId",null);
            if(available(rows).stream().noneMatch(screen -> screen.screenId().equals(input.screenId()))) throw InputValidator.invalid("screenId");
        }
        if(rows.stream().anyMatch(row -> row.level()==input.level() && Objects.equals(row.parentId(),input.parentId()) && row.name().equals(input.name())))
            throw new ApiException(409,"DUPLICATE_NAME","같은 그룹에 동일한 메뉴 이름이 있습니다.","name",null);
    }
    private String nextId(String prefix,List<Stored> rows) {
        BigInteger max=BigInteger.ZERO;
        Set<String> ids=new HashSet<>();
        for(Stored row:rows) {
            String id=row.menuId(); ids.add(id);
            if(id.matches(prefix+"[0-9]+")) max=max.max(new BigInteger(id.substring(1)));
        }
        String id;
        do {
            max=max.add(BigInteger.ONE);
            id=prefix+"0".repeat(Math.max(0,3-max.toString().length()))+max;
            if(id.length()>20) throw new ApiException(409,"SORT_CAPACITY_EXCEEDED","메뉴 식별자 발급 범위를 초과했습니다.");
        } while(ids.contains(id));
        return id;
    }
    private void menuHistory(String id,Create input,int order) {
        Map<String,String> values=new LinkedHashMap<>();
        values.put("MENU_ID",id); values.put("MENU_NM",input.name()); values.put("MENU_LVL",Integer.toString(input.level()));
        values.put("UPPER_MENU_ID",input.parentId()); values.put("SCRN_ID",input.screenId());
        values.put("SORT_ORD",Integer.toString(order)); values.put("USE_YN","Y"); values.put("LOCK_YN","N");
        values.put("PC_ONLY_YN",input.pcOnly()?"Y":"N"); values.put("ICON_NM",input.level()==1?"folder":null);
        values.forEach((field,value) -> {
            if(value!=null) history.append(new ChangeHistoryWriter.Change("TB_SY_MENU",id,"NEW",field,null,value,null));
        });
    }
}
