package com.sicost.apiops;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.apiops.PeopleHistoryDtos.*;

@Mapper
public interface PeopleHistoryMapper {
    boolean exists(Query query);
    long count(Query query);
    List<Entry> list(Query query);
}
