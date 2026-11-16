package com.sicost.receivablesops;

import com.sicost.common.notifications.ReceivableDunningCoordinator;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/** The same durable delivery path is used by the daily coordinator and explicit reminders. */
@Service
public class DunningAutomationCoordinator implements ReceivableDunningCoordinator {
    private final DunningDeliveryRepository repository;
    private final DunningDeliveryCommands commands;
    private final DunningDeliveryWorker worker;
    private final DunningDeliveryState state;
    public DunningAutomationCoordinator(DunningDeliveryRepository repository,DunningDeliveryCommands commands,
        DunningDeliveryWorker worker,DunningDeliveryState state) {
        this.repository=repository;this.commands=commands;this.worker=worker;this.state=state;
    }
    public List<String> businessKeys(int stage,LocalDate today) {return stage==1?List.of():repository.billIds();}
    public void process(int stage,String billId,LocalDate today) {
        if(stage==2)commands.automatic(billId);
        else if(stage==3) {for(var round:repository.rounds(billId))if(!round.terminal())worker.deliver(billId,round.eventKey());}
        else if(stage==4)for(var round:repository.rounds(billId))state.finalizeRound(billId,round.eventKey());
    }
    public void recoverAbandonedSending() {worker.recoverInterrupted();}
}
