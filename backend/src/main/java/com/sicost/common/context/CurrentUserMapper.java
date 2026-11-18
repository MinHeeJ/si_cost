package com.sicost.common.context;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CurrentUserMapper {
    /** Removed legacy selection API: retained only to keep historical clients source-compatible. */
    @Deprecated default String findAdministrator() { throw new com.sicost.common.api.ApiException(401,"AUTH_REQUIRED","로그인이 필요합니다."); }
    @Deprecated default List<String> findAdministratorRoles() { throw new com.sicost.common.api.ApiException(401,"AUTH_REQUIRED","로그인이 필요합니다."); }
    String findName(String userId);
    List<String> findActiveRoles(String userId);
}
