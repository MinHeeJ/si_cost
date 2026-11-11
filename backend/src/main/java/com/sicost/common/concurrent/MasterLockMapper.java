package com.sicost.common.concurrent;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MasterLockMapper {
    void lockClose();
    void lockSettlement();
}
