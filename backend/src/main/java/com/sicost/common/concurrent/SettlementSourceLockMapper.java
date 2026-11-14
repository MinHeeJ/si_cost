package com.sicost.common.concurrent;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SettlementSourceLockMapper {
    List<String> partIds(SettlementSourceLocks.Sources sources);
    List<String> lockParts(@Param("ids") List<String> ids);
    List<String> lockHeaders(@Param("ids") List<String> ids);
    List<String> lockStandards(@Param("ids") List<String> ids);
}
