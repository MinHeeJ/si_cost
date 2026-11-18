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
    private com.sicost.authentication.AuthenticationTaskService authentication;
    private com.sicost.permission.PermissionService permissions;
    @org.springframework.beans.factory.annotation.Autowired
    public void setUserTasks(com.sicost.authentication.AuthenticationTaskService authentication,com.sicost.permission.PermissionService permissions) {this.authentication=authentication;this.permissions=permissions;}
    public DunningDeliveryWorker(DunningDeliveryRepository repository,DunningDeliveryState state,DunningDeliveryMail mail,
        DunningDeliverySessionLocks sessions,@Qualifier("dunningDeliveryExecutor") TaskExecutor executor) {
        this.repository=repository;this.state=state;this.mail=mail;this.sessions=sessions;this.executor=executor;
    }
    public void submit(String billId,String eventKey) {
        Runnable work;
        if(org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() instanceof org.springframework.web.context.request.ServletRequestAttributes request) {
            var proof=authentication.capture(request.getRequest());
            Runnable check=()->{authentication.checkpoint(proof);permissions.require("UI-13","UPD");permissions.requireBill(billId);};check.run();
            work=()->authentication.run(proof,()->com.sicost.permission.PermissionTaskContext.user(check,()->{deliver(billId,eventKey);return null;}));
        } else {
            if(!com.sicost.permission.PermissionTaskContext.systemCommand())throw com.sicost.permission.PermissionService.denied();
            work=()->com.sicost.permission.PermissionTaskContext.system(com.sicost.permission.PermissionTaskContext.Command.RECEIVABLE_DUNNING,()->{deliver(billId,eventKey);return null;});
        }
        try {executor.execute(work);}
        catch(org.springframework.core.task.TaskRejectedException ignored) { /* Durable PENDING remains available to the same explicit UUID. */ }
    }
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void applicationReady(org.springframework.boot.context.event.ApplicationReadyEvent event) {
        if(event.getApplicationContext() instanceof org.springframework.web.context.WebApplicationContext)recoverInterrupted();
    }
    public void recoverInterrupted() {
        com.sicost.permission.PermissionTaskContext.system(com.sicost.permission.PermissionTaskContext.Command.RECEIVABLE_DUNNING,()->{
            for(var work:repository.interrupted())sessions.withLock(work.eventKey(),()->state.recoverInterrupted(work.billId(),work.eventKey()));return null;
        });
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
                com.sicost.permission.PermissionTaskContext.checkpoint();
                var prepared=state.prepare(billId,eventKey,address);
                if(prepared.isEmpty())return;
                var current=state.finalRecipients(billId);
                var denied=prepared.stream().filter(task->current.stream().noneMatch(person->person.userId().equals(task.userId())&&Objects.equals(person.userName(),task.userName())&&DunningDeliveryState.normalized(person.emailAddress()).equals(address))).toList();
                if(!denied.isEmpty())state.complete(billId,eventKey,denied,new DunningDeliveryMail.MailResult("SKIPPED","PERMISSION_REVOKED","발송 직전 수신 상태·자격·메뉴 또는 담당 범위가 변경되었습니다."));
                var selected=prepared.stream().filter(task->!denied.contains(task)).toList();
                if(selected.isEmpty())return;
                // prepare has committed SENDING and released every financial/bill lock before this call.
                try {com.sicost.permission.PermissionTaskContext.checkpoint();}
                catch(RuntimeException revoked) {state.complete(billId,eventKey,selected,new DunningDeliveryMail.MailResult("SKIPPED","REQUESTER_PERMISSION_REVOKED","발송 요청자의 인증 또는 권한이 변경되어 발송하지 않았습니다."));return;}
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
