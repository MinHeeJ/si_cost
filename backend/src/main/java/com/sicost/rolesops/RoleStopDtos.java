package com.sicost.rolesops;

import java.time.LocalDateTime;
import java.util.List;

public final class RoleStopDtos {
    private RoleStopDtos() {}
    public record Request(String expectedRevision) {}
    public record Mutation(String roleId,String actor,LocalDateTime now) {}
    public record Permission(String menuId,boolean inquire,boolean register,boolean update,boolean delete,
        boolean upload,boolean download,boolean confirm,String parentId,String name,String screenId,boolean use) {}
    public record Detail(String roleId,String name,String dataScope,boolean sensitiveView,boolean systemRole,
        boolean use,int sortOrder,String revision,List<Permission> menuPermissions,boolean developmentMode,
        boolean accessEnforcement,String settingsRevision) {}
    public record Result(Detail value,String revision,boolean changed) {}
}
