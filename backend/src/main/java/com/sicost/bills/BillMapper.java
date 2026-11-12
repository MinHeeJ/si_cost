package com.sicost.bills;

import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.bills.BillDtos.*;

@Mapper
public interface BillMapper {
    List<Stored> list(Filter filter);
    Stored find(@Param("id") String id);
    String revisionSnapshot(@Param("id") String id);
    List<ReceiptStored> receipts(@Param("id") String id);
    Project project(@Param("id") String id);
    List<Stored> projectBills(@Param("projectId") String projectId);
    int greatestNumber(@Param("month") String month);
    int greatestOrder(@Param("projectId") String projectId);
    BigDecimal standard(@Param("code") String code,@Param("month") String month);
    String sourceSnapshot(@Param("projectId") String projectId,@Param("month") String month);
    void lockTimeout();
    void lockClose();
    int insert(Write write);
    int update(Write write);
}
