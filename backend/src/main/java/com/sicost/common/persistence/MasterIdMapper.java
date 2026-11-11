package com.sicost.common.persistence;

import java.math.BigInteger;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MasterIdMapper {
    BigInteger nextClient();
    BigInteger nextPart();
}
