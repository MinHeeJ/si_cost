package com.sicost.common.concurrent;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AggregateLockMapper {
    String lock(@Param("key") String key);
}
