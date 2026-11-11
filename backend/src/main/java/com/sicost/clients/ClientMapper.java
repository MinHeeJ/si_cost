package com.sicost.clients;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.clients.ClientDtos.*;

@Mapper
public interface ClientMapper {
    List<Stored> list(Selection selection);
    long count(@Param("filters") Query filters);
    Stored find(@Param("id") String id, @Param("lock") boolean lock);
    Stored byBusinessNo(String businessNo);
    List<Stored> sameName(@Param("name") String name, @Param("excludedId") String excludedId);
    Labels labels(String id);
    Counts counts(@Param("id") String id, @Param("basisDate") LocalDate basisDate);
    long historyCount(String id);
    Code code(@Param("group") String group, @Param("code") String code);
    long projectReferences(String id);
    long developerReferences(String id);
    int insert(Mutation mutation);
    int update(Mutation mutation);
}
