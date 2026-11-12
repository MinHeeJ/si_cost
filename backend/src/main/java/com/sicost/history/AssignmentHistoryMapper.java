package com.sicost.history;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.history.AssignmentHistoryDtos.*;

@Mapper
public interface AssignmentHistoryMapper {
    List<Entry> list(Query query);
    long count(Query query);
    Entry entry(@Param("id") String id,@Param("sn") int sn);
    int mark(@Param("id") String id,@Param("sn") int sn);
    int append(CorrectionWrite write);
}
