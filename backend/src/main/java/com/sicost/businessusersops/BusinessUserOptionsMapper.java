package com.sicost.businessusersops;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.businessusersops.BusinessUserOptionsDtos.*;

@Mapper
public interface BusinessUserOptionsMapper {
    List<UserOption> eligible(@Param("roleId") String roleId);
    List<RoleOption> activeRoles();
}
