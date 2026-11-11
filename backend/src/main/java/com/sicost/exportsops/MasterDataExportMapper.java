package com.sicost.exportsops;

import java.time.LocalDate;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.cursor.Cursor;

@Mapper
public interface MasterDataExportMapper {
    record Query(MasterDataExportInput.Filters filters, LocalDate basisDate, String month, String sort, String direction) {}
    Cursor<Map<String, Object>> clients(Query query);
    Cursor<Map<String, Object>> parts(Query query);
}
