package com.sicost.common.security;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccountKeyMapper {
    boolean hasEncryptedAccounts();
}
