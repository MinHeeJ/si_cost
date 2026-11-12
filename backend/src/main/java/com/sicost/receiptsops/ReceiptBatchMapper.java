package com.sicost.receiptsops;

import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.receiptsops.ReceiptBatchDtos.*;

@Mapper
public interface ReceiptBatchMapper {
    BillState state(@Param("id") String id);
    Map<String,Object> bill(@Param("id") String id);
    void lockTimeout();
    void lockClose();
    int insert(ReceiptWrite value);
    int cache(CacheWrite value);
}
