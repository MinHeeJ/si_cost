package com.sicost.receipts;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.receipts.ReceiptDtos.*;

@Mapper
public interface ReceiptMapper {
    StoredBill bill(@Param("id") String id);
    Cache cache(@Param("id") String id);
    QuickSource quickSource(@Param("id") String id);
    java.math.BigDecimal operatingValue(@Param("code") String code,@Param("month") String month);
    String snapshot(@Param("id") String id);
    List<StoredReceipt> receipts(@Param("id") String id);
    int insert(java.util.Map<String,Object> write);
    int cancel(Cancellation write);
    int updateCache(CacheWrite write);
    void lockClose();
    void lockTimeout();
}
