package com.sicost.common.concurrent;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DeveloperRevisionMapper {
    String developer(@Param("id") String id, @Param("lock") boolean lock);
    List<String> prices(@Param("id") String id, @Param("lock") boolean lock);
    List<String> skills(@Param("id") String id, @Param("lock") boolean lock);
    List<String> states(@Param("id") String id, @Param("lock") boolean lock);
}
