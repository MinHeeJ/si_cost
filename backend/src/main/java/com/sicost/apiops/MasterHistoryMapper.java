package com.sicost.apiops;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.apiops.MasterHistoryDtos.*;

@Mapper
public interface MasterHistoryMapper {
    boolean exists(Query query);
    long count(Query query);
    List<Entry> list(Query query);
}
