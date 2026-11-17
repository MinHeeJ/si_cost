package com.sicost.permission;

import com.sicost.common.TimeConfiguration;
import com.sicost.notificationsops.NotificationDtos.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Projects stored free text only after current menu and target authorization. Never rewrites history. */
@Service
public class NotificationProjectionService {
    public static final String UNAVAILABLE="현재 대상 조회 권한이 없는 알림입니다";
    private final PermissionService policies;
    private final PermissionMapper scopes;
    private final NotificationTargetMapper targets;
    private final Clock clock;
    public NotificationProjectionService(PermissionService policies,PermissionMapper scopes,NotificationTargetMapper targets,Clock clock) {
        this.policies=policies;this.scopes=scopes;this.targets=targets;this.clock=clock;
    }
    public List<Item> project(List<Item> items) {
        var capabilities=policies.capabilities();String user=policies.userId();
        return items.stream().map(item->project(item,capabilities,user)).toList();
    }
    public Item project(Item item) {return project(item,policies.capabilities(),policies.userId());}
    private Item project(Item item,PermissionService.Capabilities capabilities,String user) {
        boolean allowed=allowed(item,capabilities,user);
        String content=switch(item.typeCode()) {
            case "UPLD_DONE" -> "업로드 반영 완료";
            case "AI_DONE" -> "AI 예측 완료";
            case "QUOTE_REJ" -> !allowed?UNAVAILABLE:capabilities.sensitive()?item.content():"견적 검토 결과가 도착했습니다.";
            default -> allowed?item.content():UNAVAILABLE;
        };
        return new Item(item.id(),item.typeCode(),content,item.sendMethodCode(),item.read(),item.readAt(),item.notifiedAt(),allowed?item.link():null);
    }
    private boolean allowed(Item item,PermissionService.Capabilities capabilities,String user) {
        Link link=item.link();
        if(link==null || link.screenId()==null || !capabilities.screens().getOrDefault(link.screenId(),Set.of()).contains("INQ"))return false;
        String key=link.linkKey();
        if(key==null || !key.matches("[A-Za-z0-9_-]{1,50}"))return false;
        return switch(item.typeCode()) {
            case "DUN" -> link.screenId().equals("UI-13") && project(targets.bill(key),capabilities);
            case "WTHDR" -> link.screenId().equals("UI-05") && project(targets.assignment(key),capabilities);
            case "CASH_SHORT" -> link.screenId().equals("UI-21") && capabilities.scope().all() && key.matches("[0-9]{4}-(0[1-9]|1[0-2])");
            case "QUOTE_REJ" -> {
                var target=link.screenId().equals("UI-29")?targets.opportunity(key):null;
                yield target!=null && (capabilities.scope().all() || target.partId()!=null && capabilities.scope().partIds().contains(target.partId()) || target.projectId()!=null && capabilities.scope().projectIds().contains(target.projectId()));
            }
            case "UPLD_DONE" -> {
                var target=key.matches("[0-9]{1,12}")?targets.upload(key):null;
                // Legacy upload previews do not yet support limited field projection.
                yield target!=null && user.equals(target.userId()) && link.screenId().equals(target.screenId()) && capabilities.screens().getOrDefault(target.screenId(),Set.of()).contains("UPLD") && capabilities.scope().all() && capabilities.sensitive();
            }
            // An AI answer's saved numeric conclusion needs the source policy, not
            // just UI-26. Until that link can be proven safe, retain completion only.
            case "AI_DONE" -> false;
            default -> false;
        };
    }
    private boolean project(NotificationTargetMapper.Target target,PermissionService.Capabilities capabilities) {
        if(target==null || target.projectId()==null)return false;
        if(capabilities.scope().all() || capabilities.scope().projectIds().contains(target.projectId()))return true;
        String month=target.month()==null?YearMonth.now(clock.withZone(TimeConfiguration.SEOUL)).toString().replace("-",""):target.month();
        if(!month.matches("[0-9]{4}(0[1-9]|1[0-2])"))return false;
        String part=scopes.projectPart(target.projectId(),month);
        return part!=null && capabilities.scope().partIds().contains(part);
    }
}
