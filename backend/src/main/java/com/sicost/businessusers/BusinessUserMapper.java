package com.sicost.businessusers;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.businessusers.BusinessUserDtos.*;

@Mapper
public interface BusinessUserMapper {
    Role findRole(String roleId);
    long activeAdministratorCount();
    int greatestScopeSn(String userId);
    Integer headOfficeScopeSn(String userId);
    int insertHeadOffice(HeadOfficeWrite write);
    int deleteHeadOffice(HeadOfficeWrite write);
    List<Stored> list(Filter filter);
    long count(Filter filter);
    Stored find(@Param("userId") String userId, @Param("lock") boolean lock);
    List<Role> roles(String userId);
    String snapshot(String userId);
    boolean tempPassword(String userId);
    boolean headOffice(String userId);
    List<AssignedPart> assignedParts(@Param("userId") String userId, @Param("month") String month);
    List<Role> availableRoles();
    String lockUser(String userId);
    List<String> connectedProjects(String userId);
    int insert(NewUser user);
    int update(Mutation mutation);
    int link(RoleLink link);
    int unlink(RoleLink link);
    List<String> leaderParts(@Param("userId") String userId, @Param("month") String month);
}
