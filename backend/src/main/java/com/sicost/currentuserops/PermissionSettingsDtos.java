package com.sicost.currentuserops;

import java.util.List;

public final class PermissionSettingsDtos {
    private PermissionSettingsDtos() {}
    public record Role(String roleId, String dataScope, boolean sensitiveView) {}
    public record MenuPermission(String menuId, boolean inquire, boolean register, boolean update,
                                 boolean delete, boolean upload, boolean download, boolean confirm) {}
    public record AssignedPart(String partId, String name, boolean headOffice) {}
    public record AssignedProject(String projectId, int scopeSn) {}
    public record ScopeUnion(boolean all, List<String> partIds, List<String> projectIds) {}
    public record Settings(String userId, List<String> roleIds, List<MenuPermission> menuPermissions,
                           boolean sensitiveView, ScopeUnion rowScope, List<AssignedPart> assignedParts,
                           List<AssignedProject> assignedProjects, boolean developmentMode,
                           boolean accessEnforcement, String settingsRevision) {}
    public record Snapshot(String stored, List<AssignedPart> assignedParts, List<AssignedProject> assignedProjects) {}
}
