package com.sicost.common.context;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CurrentUserMapper {
    String findAdministrator();
    List<String> findAdministratorRoles();
}
