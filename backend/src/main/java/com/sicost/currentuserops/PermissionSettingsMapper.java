package com.sicost.currentuserops;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.currentuserops.PermissionSettingsDtos.*;

@Mapper
public interface PermissionSettingsMapper {
    List<Role> roles(String userId);
    List<MenuPermission> menuPermissions(String userId);
    List<AssignedPart> assignedParts(@Param("userId") String userId, @Param("month") String month);
    List<AssignedProject> assignedProjects(String userId);
    String snapshot(String userId);
}
