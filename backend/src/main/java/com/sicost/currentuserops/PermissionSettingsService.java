package com.sicost.currentuserops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.context.CurrentUserProvider;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.currentuserops.PermissionSettingsDtos.*;

@Service
public class PermissionSettingsService {
    private final PermissionSettingsRepository repository;
    private final CurrentUserProvider users;
    private final RevisionService revisions;
    private final Clock clock;
    private com.sicost.permission.PermissionService permissions;
    @org.springframework.beans.factory.annotation.Autowired
    public void setPermissions(com.sicost.permission.PermissionService permissions) {this.permissions=permissions;}
    public PermissionSettingsService(PermissionSettingsRepository repository,CurrentUserProvider users,
                                     RevisionService revisions,Clock clock) {
        this.repository=repository;this.users=users;this.revisions=revisions;this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Settings getCurrentUserPermissionSettings() {
        String userId=users.currentUser().userId();
        List<Role> roles=repository.roles(userId);
        String month=YearMonth.now(clock.withZone(TimeConfiguration.SEOUL)).format(DateTimeFormatter.ofPattern("yyyyMM"));
        List<AssignedPart> parts=repository.assignedParts(userId,month);
        List<AssignedProject> projects=repository.assignedProjects(userId);
        boolean all=roles.stream().anyMatch(role->"ALL".equals(role.dataScope()));
        List<String> partIds=parts.stream().filter(part->roles.stream().anyMatch(role->"PART".equals(role.dataScope())))
            .map(AssignedPart::partId).distinct().sorted().toList();
        List<String> projectIds=projects.stream().filter(project->roles.stream().anyMatch(role->"PROJ".equals(role.dataScope())))
            .map(AssignedProject::projectId).distinct().sorted().toList();
        List<MenuPermission> menus=repository.menuPermissions(userId);
        if(permissions!=null) {
            var allowed=permissions.menuActions();
            menus=menus.stream().map(menu->{var actions=allowed.getOrDefault(menu.menuId(),java.util.Set.of());return new MenuPermission(menu.menuId(),actions.contains("INQ"),actions.contains("REG"),actions.contains("UPD"),actions.contains("DEL"),actions.contains("UPLD"),actions.contains("DNLD"),actions.contains("CNFM"));}).toList();
        }
        return new Settings(userId,roles.stream().map(Role::roleId).distinct().sorted().toList(),menus,
            roles.stream().anyMatch(Role::sensitiveView),new ScopeUnion(all,partIds,projectIds),parts,projects,false,true,
            revisions.revision(new Snapshot(repository.snapshot(userId),parts,projects)));
    }
}
