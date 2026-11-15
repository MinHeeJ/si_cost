package com.sicost.receivablesops;

import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class DunningDeliveryWorker {
    private final DunningDeliveryRepository repository;
    private final DunningDeliveryState state;
    private final DunningDeliveryMail mail;
    private final DunningDeliverySessionLocks sessions;
    private final TaskExecutor executor;
    public DunningDeliveryWorker(DunningDeliveryRepository repository,DunningDeliveryState state,DunningDeliveryMail mail,
        DunningDeliverySessionLocks sessions,@Qualifier("dunningDeliveryExecutor") TaskExecutor executor) {
        this.repository=repository;this.state=state;this.mail=mail;this.sessions=sessions;this.executor=executor;
    }
    public void submit(String billId,String eventKey) {
        try {executor.execute(()->deliver(billId,eventKey));}
        catch(org.springframework.core.task.TaskRejectedException ignored) { /* Durable PENDING remains available to the same explicit UUID. */ }
    }
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        for(var work:repository.interrupted())sessions.withLock(work.eventKey(),()->state.recoverInterrupted(work.billId(),work.eventKey()));
    }
    /** Called only by the owning 09:00 automation stage, never by startup. */
    public void resumeAutomatic() {
        for(var work:repository.automaticPending())deliver(work.billId(),work.eventKey());
    }
    public void deliver(String billId,String eventKey) {
        sessions.withLock(eventKey,()->{
            var addresses=repository.tasks(billId).stream().filter(t->t.eventKey().equals(eventKey)&&t.channelCode().equals("MAIL")
                &&Set.of("PENDING","SENDING").contains(t.state())).map(t->DunningDeliveryState.normalized(t.emailAddress())).distinct().sorted().toList();
            for(String address:addresses)sessions.withLock(eventKey+":"+address,()->{
                var selected=state.prepare(billId,eventKey,address);
                if(selected.isEmpty())return;
                // prepare has committed SENDING and released every financial/bill lock before this call.
                var result=mail.send(selected.get(0));
                try {state.complete(billId,eventKey,selected,result);}
                catch(RuntimeException failedReceipt) {
                    state.complete(billId,eventKey,selected,new DunningDeliveryMail.MailResult("UNKNOWN","MAIL_ACCEPTANCE_UNKNOWN","발송 결과 저장을 확인하지 못했습니다. 자동 재발송하지 않습니다."));
                }
            });
            state.finalizeRound(billId,eventKey);
        });
    }
}
