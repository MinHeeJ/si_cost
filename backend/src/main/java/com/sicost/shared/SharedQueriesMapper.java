package com.sicost.shared;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.sicost.shared.SharedQueryDtos.*;

@Mapper
public interface SharedQueriesMapper {
    List<CodeLookup> codes(@Param("group") String group, @Param("includeInactive") boolean includeInactive);
    Parameter parameter(@Param("key") String key, @Param("month") String month);
    String cashBaseMonth();
    List<CloseRow> monthCloses(@Param("from") String from, @Param("to") String to);
    List<MenuRow> menus();
    List<History> history(HistoryQuery query);
    long historyCount(HistoryQuery query);
}
