package com.sicost.salesparts;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.salesparts.SalesPartDtos.*;

@Mapper
public interface SalesPartMapper {
    List<Stored> list(Query query);
    Stored part(@Param("id") String id, @Param("lock") boolean lock);
    List<StoredStandard> standards(@Param("id") String id, @Param("lock") boolean lock);
    String leaderName(String id);
    String lockUser(String id);
    boolean eligible(String id);
    boolean nameExists(String name);
    Counts counts(@Param("id") String id, @Param("month") String month, @Param("future") boolean future);
    String cashMonth();
    String lastClose();
    int insertPart(Registration registration);
    int insertStandard(Registration registration);
    int update(Mutation mutation);
}
