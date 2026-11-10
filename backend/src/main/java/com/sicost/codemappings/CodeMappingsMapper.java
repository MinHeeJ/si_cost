package com.sicost.codemappings;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CodeMappingsMapper {
    record Query(String sourceText, String groupId, int size, long offset) {}
    record Insert(String sourceText, String groupId, String code, String userId, LocalDateTime occurredAt) {}
    List<CodeMappingRow> list(Query query);
    long count(Query query);
    String activeCodeName(@Param("groupId") String groupId, @Param("code") String code);
    Long insert(Insert row);
    CodeMappingRow lock(@Param("mapId") long mapId);
    int delete(@Param("mapId") long mapId);
}
