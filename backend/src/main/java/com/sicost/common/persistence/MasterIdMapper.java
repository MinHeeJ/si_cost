package com.sicost.common.persistence;

import java.math.BigInteger;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MasterIdMapper {
    BigInteger nextClient();
    BigInteger nextPart();
    BigInteger nextDeveloper();
    BigInteger nextCandidate();
    BigInteger nextManagementNo();
    BigInteger nextAssignment();
    boolean managementNoExists(String managementNo);
}
