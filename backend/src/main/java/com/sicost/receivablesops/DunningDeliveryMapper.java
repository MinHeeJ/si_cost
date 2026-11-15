package com.sicost.receivablesops;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.receivablesops.DunningDeliveryDtos.*;

@Mapper
public interface DunningDeliveryMapper {
    BillSource bill(String billId);
    String lockBill(String billId);
    String snapshot(String billId);
    List<RoundSource> rounds(String billId);
    List<TaskSource> tasks(String billId);
    List<DeliveryHistory> histories(String billId);
    List<DeliveryWork> interrupted();
    List<DeliveryWork> automaticPending();
    int nextSerial(String billId);
    int insertMail(MailReservation row);
    int transition(TaskTransition row);
    int insertHistory(HistoryInsert row);
}
