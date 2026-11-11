package com.sicost.common.history;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChangeHistoryMapper {
    int insert(ChangeHistoryEntry entry);
}
