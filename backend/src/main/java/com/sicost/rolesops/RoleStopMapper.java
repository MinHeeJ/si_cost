package com.sicost.rolesops;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.rolesops.RoleStopDtos.*;

@Mapper
public interface RoleStopMapper {
    long connectedUsers(String roleId);
    List<Permission> menuPermissions(String roleId);
    int stop(Mutation mutation);
}
