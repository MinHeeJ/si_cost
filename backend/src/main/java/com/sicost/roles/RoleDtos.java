package com.sicost.roles;

import java.time.LocalDateTime;
import java.util.List;

public final class RoleDtos {
    private RoleDtos() {}
    public record Stored(String roleId,String name,String dataScope,String sensitiveYn,String systemYn,String useYn,int sortOrder) {}
    public record Permission(String menuId,boolean inquire,boolean register,boolean update,boolean delete,
                             boolean upload,boolean download,boolean confirm,String parentId,String name,String screenId,boolean use) {
        public PermissionInput input() {return new PermissionInput(menuId,inquire,register,update,delete,upload,download,confirm);}
    }
    public record PermissionInput(String menuId,boolean inquire,boolean register,boolean update,boolean delete,
                                  boolean upload,boolean download,boolean confirm) {}
    public record ManagedRole(String roleId,String name,String dataScope,boolean sensitiveView,boolean systemRole,
                              boolean use,int sortOrder,String revision) {}
    public record ManagedRolesData(List<ManagedRole> items,boolean developmentMode,boolean accessEnforcement,String settingsRevision) {}
    public record ManagedRoleDetail(String roleId,String name,String dataScope,boolean sensitiveView,boolean systemRole,
                                    boolean use,int sortOrder,String revision,List<Permission> menuPermissions,
                                    boolean developmentMode,boolean accessEnforcement,String settingsRevision) {}
    public record ManagedRoleSaveResult(ManagedRoleDetail value,String revision,boolean changed) {}
    public record Create(String mode,String name,String dataScope,String sourceRoleId,String sourceRevision) {}
    public record Edit(String dataScope,boolean sensitiveView,List<PermissionInput> menuPermissions,String expectedRevision) {}
    public record RoleWrite(String roleId,Stored value,String actor,LocalDateTime now) {}
    public record PermissionWrite(String roleId,PermissionInput value,String actor,LocalDateTime now) {}
}
