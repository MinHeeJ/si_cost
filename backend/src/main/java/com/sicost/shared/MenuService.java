package com.sicost.shared;

import com.sicost.shared.SharedQueryDtos.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuService {
    public static final Set<String> SCREEN_IDS = Set.of("UI-16","UI-15","UI-02","UI-01","UI-31","UI-12","UI-05","UI-11","UI-28","UI-17","UI-29","UI-33","UI-13","UI-03","UI-04","UI-19","UI-32","UI-10","UI-07","UI-08","UI-09","UI-18","UI-21","UI-14","UI-26","UI-30","UI-23","UI-24","UI-25","UI-27");
    private static final Comparator<MenuRow> ORDER = Comparator.comparingInt(MenuRow::sortOrder).thenComparing(MenuRow::menuId);
    private final SharedQueriesMapper mapper;
    public MenuService(SharedQueriesMapper mapper) { this.mapper=mapper; }
    @Transactional(readOnly=true)
    public MenusData getMenus() {
        List<MenuRow> rows=mapper.menus();
        Map<String,MenuRow> byId=new HashMap<>();
        rows.forEach(row -> byId.put(row.menuId(),row));
        Map<String,String> excluded=new LinkedHashMap<>();
        for (MenuRow row:rows) {
            String reason=invalid(row,byId);
            if (reason!=null) excluded.put(row.menuId(),reason);
        }
        Map<String,MenuRow> winners=new HashMap<>();
        rows.stream().filter(row -> !excluded.containsKey(row.menuId()) && row.screenId()!=null).sorted(ORDER).forEach(row -> {
            if (winners.putIfAbsent(row.screenId(),row)!=null) excluded.put(row.menuId(),"중복 화면의 대표 메뉴가 아니므로 제외했습니다.");
        });
        boolean changed;
        do {
            changed=false;
            for (MenuRow row:rows) if (!excluded.containsKey(row.menuId()) && excluded.containsKey(row.parentId())) {
                excluded.put(row.menuId(),"제외된 부모 메뉴의 자손입니다."); changed=true;
            }
        } while(changed);
        Map<String,List<MenuRow>> children=new HashMap<>();
        for (MenuRow row:rows) if (!excluded.containsKey(row.menuId())) children.computeIfAbsent(row.parentId(),key -> new ArrayList<>()).add(row);
        children.values().forEach(list -> list.sort(ORDER));
        List<MenuNode> roots=children.getOrDefault(null,Collections.emptyList()).stream().map(row -> node(row,children)).toList();
        return new MenusData(roots,excluded.entrySet().stream().map(e -> new MenuDiagnostic(e.getKey(),e.getValue())).toList());
    }
    private String invalid(MenuRow row,Map<String,MenuRow> byId) {
        Set<String> visited=new HashSet<>();
        MenuRow current=row;
        int depth=0;
        while (current!=null) {
            if (!visited.add(current.menuId())) return "자기참조 또는 순환 메뉴와 자손을 제외했습니다.";
            if (!"Y".equals(current.useYn())) return "중지 메뉴 또는 중지 부모의 자손을 제외했습니다.";
            if (current.screenId()!=null && !SCREEN_IDS.contains(current.screenId())) return "미등록 화면 메뉴와 자손을 제외했습니다.";
            depth++;
            if (current.parentId()==null) break;
            current=byId.get(current.parentId());
            if (current==null) return "부모가 없는 메뉴와 자손을 제외했습니다.";
        }
        current=row;
        for(int expected=depth;current!=null;expected--) {
            if(current.level()!=expected) return "메뉴 깊이가 MENU_LVL과 일치하지 않습니다.";
            current=current.parentId()==null?null:byId.get(current.parentId());
        }
        return null;
    }
    private MenuNode node(MenuRow row,Map<String,List<MenuRow>> children) {
        return new MenuNode(row.menuId(),row.parentId(),row.name(),row.screenId(),row.sortOrder(),row.icon(),row.pcOnly(),
            children.getOrDefault(row.menuId(),Collections.emptyList()).stream().map(child -> node(child,children)).toList());
    }
}
