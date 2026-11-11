package com.sicost.operatingstandards;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.operatingstandards.OperatingStandardsDtos.*;

@Mapper
public interface OperatingStandardsMapper {
    List<Stored> all();
    List<Stored> rows(@Param("key") String key, @Param("lock") boolean lock);

    List<String> closedMonths();
    int update(Mutation mutation);
    int insert(Mutation mutation);
}
