package com.sicost.billsops;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.billsops.BillOperationsDtos.*;

@Mapper
public interface BillOperationsMapper {
    Project project(String id);
    StoredBill bill(String id);
    String snapshot(@Param("id") String id);
    List<StoredBill> bills();
    List<Receipt> receipts(String id);
    List<String> assignments(String projectId);
    Developer developer(String id);
    String latest(String projectId);
    int targetCount(@Param("projectId") String projectId,@Param("month") String month);
    boolean fixed(@Param("projectId") String projectId,@Param("month") String month);
    int greatestNumber(String month);
    int greatestOrder(String projectId);
    void lockClose();
    void lockTimeout();
    int cancel(Cancellation input);
    int insert(NewBill input);
}
