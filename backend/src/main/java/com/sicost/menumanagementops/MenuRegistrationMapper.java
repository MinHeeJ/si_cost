package com.sicost.menumanagementops;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.menumanagementops.MenuRegistrationDtos.*;

@Mapper
public interface MenuRegistrationMapper {
    List<String> roleIds();
    int insertMenu(NewMenu menu);
    int insertPermission(NewPermission permission);
}
