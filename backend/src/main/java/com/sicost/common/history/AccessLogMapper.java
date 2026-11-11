package com.sicost.common.history;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccessLogMapper {
    int insert(AccessLogWriter.Entry entry);
}
