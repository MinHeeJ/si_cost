package com.sicost.roles;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.roles.RoleDtos.*;

@Mapper
public interface RoleMapper {
    List<Stored> roles();
    Stored find(String roleId);
    List<Permission> permissions(String roleId);
    String snapshot(String roleId);
    String settingsSnapshot();
    int insert(RoleWrite row);
    int update(RoleWrite row);
    int insertPermission(PermissionWrite row);
    int updatePermission(PermissionWrite row);
    boolean permissionExists(PermissionWrite row);
}
