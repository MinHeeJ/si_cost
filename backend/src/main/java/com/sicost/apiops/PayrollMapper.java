package com.sicost.apiops;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.apiops.PayrollDtos.*;

@Mapper
public interface PayrollMapper {
    List<Row> rows(Query query);
    long count(Query query);
    String snapshot(String month);
}
