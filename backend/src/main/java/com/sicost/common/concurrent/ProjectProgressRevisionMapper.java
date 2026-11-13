package com.sicost.common.concurrent;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectProgressRevisionMapper {
    String snapshot(@Param("id") String id);
}
