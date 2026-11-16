package com.sicost.menumanagement;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.validation.InputValidator;
import com.sicost.shared.MenuService;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.menumanagement.MenuManagementDtos.*;

@Service
public class MenuManagementService {
    private static final Set<String> PROTECTED_SCREENS = Set.of("UI-23", "UI-24", "UI-25", "UI-27");
    private final MenuManagementRepository repository;
    private final WriteCoordinator writes;
    private final RevisionService revisions;
    private final CurrentUserProvider users;
    private final ChangeHistoryWriter history;
    private final Clock clock;
    public MenuManagementService(MenuManagementRepository repository, WriteCoordinator writes, RevisionService revisions,
                                 CurrentUserProvider users, ChangeHistoryWriter history, Clock clock) {
        this.repository=repository; this.writes=writes; this.revisions=revisions; this.users=users; this.history=history; this.clock=clock;
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public ManagedMenuTree getMenuManagement() {
        users.currentUser();
        return view(repository.tree());
    }
    @Transactional
    public SaveResult putMenuManagement(PutTree input) {
        String actor=users.userId();
        List<Stored> before=new ArrayList<>();
        List<MenuInput> normalized=new ArrayList<>();
        var result=writes.write(List.of(SettlementSourceLocks.ROLE_RESOURCE, SettlementSourceLocks.MENU_RESOURCE),
            input.expectedRevision(), () -> repository.tree(), () -> {
                before.addAll(repository.tree());
                normalized.addAll(validate(input.items(),before));
            }, () -> normalized.stream().anyMatch(row -> changed(find(before,row.menuId()),row)), () -> {
                LocalDateTime now=LocalDateTime.ofInstant(clock.instant(),ZoneId.of("Asia/Seoul"));
                for (MenuInput row:normalized) {
                    Stored old=find(before,row.menuId());
                    if (!changed(old,row)) continue;
                    repository.update(new Mutation(row,row.level()==1 ? yn(old.use()) : yn(row.use()),
                        row.level()==1 ? yn(old.pcOnly()) : yn(row.pcOnly()),actor,now));
                    audit(old.menuId(),"MENU_NM",old.name(),row.name());
                    audit(old.menuId(),"UPPER_MENU_ID",old.parentId(),row.parentId());
                    audit(old.menuId(),"SORT_ORD",Integer.toString(old.sortOrder()),Integer.toString(row.sortOrder()));
                    if(row.level()==2) {
                        audit(old.menuId(),"USE_YN",yn(old.use()),yn(row.use()));
                        audit(old.menuId(),"PC_ONLY_YN",yn(old.pcOnly()),yn(row.pcOnly()));
                    }
                }
            });
        ManagedMenuTree value=view(repository.tree());
        return new SaveResult(value,result.revision(),result.changed());
    }
    private List<MenuInput> validate(List<MenuInput> requested,List<Stored> stored) {
        Map<String,Stored> byId=new HashMap<>();stored.forEach(row->byId.put(row.menuId(),row));
        Set<String> ids=new HashSet<>();
        for(MenuInput row:requested) if(!ids.add(row.menuId()) || !byId.containsKey(row.menuId())) throw InputValidator.invalid("items");
        if(!ids.equals(byId.keySet())) throw InputValidator.invalid("items");
        Set<String> names=new HashSet<>(), orders=new HashSet<>();
        Map<String,List<MenuInput>> siblings=new HashMap<>();
        for(int index=0;index<requested.size();index++) {
            MenuInput row=requested.get(index);Stored old=byId.get(row.menuId());String field="items."+index+".";
            if(row.level()!=old.level()) throw InputValidator.invalid(field+"level");
            if(!Objects.equals(row.screenId(),old.screenId())) throw InputValidator.invalid(field+"screenId");
            if(row.level()==1) {
                if(row.parentId()!=null || row.screenId()!=null || row.use()!=null || row.pcOnly()!=null) throw InputValidator.invalid(field+"parentId");
            } else {
                Stored parent=byId.get(row.parentId());
                if(parent==null || parent.level()!=1 || row.menuId().equals(row.parentId())) throw InputValidator.invalid(field+"parentId");
                if(row.screenId()==null || !MenuService.SCREEN_IDS.contains(row.screenId())) throw InputValidator.invalid(field+"screenId");
                if(row.use()==null || row.pcOnly()==null) throw InputValidator.invalid(field+"use");
                if(protectedMenu(old) && (!row.use() || !Objects.equals(old.parentId(),row.parentId())))
                    throw new ApiException(409,"MENU_LOCKED","잠금 메뉴는 사용을 중지하거나 시스템 그룹 밖으로 이동할 수 없습니다.",field+"use",null);
            }
            String name=InputValidator.text(row.name(),50,field+"name");
            String group=row.level()==1 ? "root" : "child:"+row.parentId();
            if(!names.add(group+"\u0000"+name)) throw new ApiException(409,"DUPLICATE_NAME","같은 그룹에 동일한 메뉴 이름이 있습니다.",field+"name",null);
            if(row.sortOrder()<1 || row.sortOrder()>99999 || !orders.add(group+":"+row.sortOrder())) throw InputValidator.invalid(field+"sortOrder");
            siblings.computeIfAbsent(group,key->new ArrayList<>()).add(new MenuInput(row.menuId(),name,row.level(),row.parentId(),row.screenId(),row.sortOrder(),row.use(),row.pcOnly()));
        }
        List<MenuInput> result=new ArrayList<>();
        for(List<MenuInput> group:siblings.values()) {
            group.sort(Comparator.comparingInt(MenuInput::sortOrder));
            for(int i=0;i<group.size();i++) {
                MenuInput row=group.get(i);
                result.add(new MenuInput(row.menuId(),row.name(),row.level(),row.parentId(),row.screenId(),i+1,row.use(),row.pcOnly()));
            }
        }
        result.sort(Comparator.comparing(MenuInput::menuId));return result;
    }
    private boolean protectedMenu(Stored row) {return row.locked() || PROTECTED_SCREENS.contains(row.screenId());}
    private Stored find(List<Stored> rows,String id) {return rows.stream().filter(row->row.menuId().equals(id)).findFirst().orElseThrow();}
    private boolean changed(Stored old,MenuInput row) {
        return !Objects.equals(old.name(),row.name()) || !Objects.equals(old.parentId(),row.parentId()) || old.sortOrder()!=row.sortOrder()
            || row.level()==2 && (old.use()!=row.use() || old.pcOnly()!=row.pcOnly());
    }
    private void audit(String id,String item,String before,String after) {
        if(!Objects.equals(before,after)) history.append(new ChangeHistoryWriter.Change("TB_SY_MENU",id,"UPD",item,before,after,null));
    }
    private static String yn(boolean value) {return value?"Y":"N";}
    private ManagedMenuTree view(List<Stored> rows) {
        Comparator<Stored> order=Comparator.comparingInt(Stored::sortOrder).thenComparing(Stored::menuId);
        List<Stored> ordered=new ArrayList<>();
        rows.stream().filter(row->row.level()==1).sorted(order).forEach(root->{
            ordered.add(root);rows.stream().filter(row->Objects.equals(row.parentId(),root.menuId())).sorted(order).forEach(ordered::add);
        });
        rows.stream().filter(row->!ordered.contains(row)).forEach(ordered::add);
        return new ManagedMenuTree(ordered.stream().map(row->new ManagedMenu(row.menuId(),row.parentId(),row.name(),row.level(),row.screenId(),
            row.sortOrder(),row.use(),row.pcOnly(),row.locked(),row.icon())).toList(),revisions.revision(rows),true,false);
    }
}
