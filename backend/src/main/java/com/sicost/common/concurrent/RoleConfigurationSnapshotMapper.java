package com.sicost.common.concurrent;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RoleConfigurationSnapshotMapper {
    Map<String,Object> role(String roleId);
    List<Map<String,Object>> permissions(String roleId);
    String storedSnapshot(String roleId);
    String settings();
}
