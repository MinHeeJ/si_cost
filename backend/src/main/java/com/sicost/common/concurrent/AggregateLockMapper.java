package com.sicost.common.concurrent;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AggregateLockMapper {
    @Select("SELECT pg_advisory_xact_lock(hashtextextended(#{key}, 0))::text")
    String lock(@Param("key") String key);
}
