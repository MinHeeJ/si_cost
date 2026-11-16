package com.sicost.currentuserops;

import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.currentuserops.PermissionSettingsDtos.*;

@Repository
public class PermissionSettingsRepository {
    private final PermissionSettingsMapper mapper;
    public PermissionSettingsRepository(PermissionSettingsMapper mapper) { this.mapper=mapper; }
    public List<Role> roles(String userId) { return mapper.roles(userId); }
    public List<MenuPermission> menuPermissions(String userId) { return mapper.menuPermissions(userId); }
    public List<AssignedPart> assignedParts(String userId,String month) { return mapper.assignedParts(userId,month); }
    public List<AssignedProject> assignedProjects(String userId) { return mapper.assignedProjects(userId); }
    public String snapshot(String userId) { return mapper.snapshot(userId); }
}
