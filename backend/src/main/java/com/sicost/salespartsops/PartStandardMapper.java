package com.sicost.salespartsops;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.salespartsops.PartStandardDtos.*;

@Mapper
public interface PartStandardMapper {
    List<Option> options();
    BigDecimal operatingValue(@Param("key") String key,@Param("month") String month);
    String lastClose();
    String lockUser(String userId);
    boolean eligible(String userId);
    Header header(@Param("id") String id,@Param("lock") boolean lock);
    List<Stored> timeline(@Param("id") String id,@Param("lock") boolean lock);
    List<String> blockedMonths(@Param("id") String id,@Param("month") String month);
    String leaderName(String userId);
    long personCount(String id);
    long projectCount(@Param("id") String id,@Param("month") String month);
    int endPrevious(Mutation mutation);
    int insert(Mutation mutation);
}
