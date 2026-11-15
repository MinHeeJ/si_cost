package com.sicost.receivablesops;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.receivablesops.DunningDeliveryDtos.*;

@Service
public class DunningDeliveryState {
    private final DunningDeliveryRepository repository;
    private final DunningDeliveryService service;
    private final DunningDeliveryLocks locks;
    public DunningDeliveryState(DunningDeliveryRepository repository,DunningDeliveryService service,DunningDeliveryLocks locks) {
        this.repository=repository;this.service=service;this.locks=locks;
    }
    @Transactional
    public List<TaskSource> prepare(String billId,String eventKey,String address) {
        locks.acquire(billId);
        var tasks=repository.tasks(billId).stream().filter(t->t.eventKey().equals(eventKey)&&t.channelCode().equals("MAIL")&&normalized(t.emailAddress()).equals(address)).toList();
        if(tasks.stream().anyMatch(t->t.state().equals("SENDING"))) {
            for(var task:tasks)if(task.state().equals("SENDING")||task.state().equals("PENDING"))change(task,"UNKNOWN",task.messageId(),null,"MAIL_ACCEPTANCE_UNKNOWN","중단된 발송입니다. 접수 여부를 확인해 주세요.");
            return Collections.emptyList();
        }
        var prior=tasks.stream().filter(t->DunningDeliveryDtos.terminal(t.state())&&!t.state().equals("SKIPPED")).findFirst().orElse(null);
        if(prior!=null) {
            for(var task:tasks)if(task.state().equals("PENDING"))change(task,prior.state(),prior.messageId(),prior.sentAt(),prior.errorCode(),prior.errorMessage());
            return Collections.emptyList();
        }
        var preview=service.preview(billId);var accepted=new ArrayList<TaskSource>();
        for(var task:tasks) {
            if(!task.state().equals("PENDING"))continue;
            var person=preview.recipients().stream().filter(r->r.userId().equals(task.userId())).findFirst().orElse(null);
            boolean matches=preview.currentStep()==task.step()&&new java.math.BigDecimal(preview.unpaidAmount()).signum()>0
                &&preview.unavailableReasons().stream().noneMatch(s->s.contains("취소"))&&person!=null
                &&Objects.equals(person.userName(),task.userName())&&normalized(person.emailAddress()).equals(address)
                &&task.body().contains("<p>잔액: "+preview.unpaidAmount()+"원</p>")
                &&task.body().contains("<p>납기: "+preview.dueDate()+"</p>")
                &&task.body().contains("<p>프로젝트: "+DunningDeliveryMail.escape(preview.projectName())+"</p>");
            if(!matches)change(task,"SKIPPED",task.messageId(),null,"DELIVERY_SOURCE_CHANGED","확인 당시 원천 또는 수신 자격이 변경되어 건너뛰었습니다.");
            else accepted.add(task);
        }
        accepted.sort(Comparator.comparing(TaskSource::userId));
        if(!accepted.isEmpty()) {
            String messageId=accepted.get(0).messageId();
            for(var task:accepted)change(task,"SENDING",messageId,null,null,null);
        }
        return List.copyOf(accepted);
    }
    @Transactional
    public void complete(String billId,String eventKey,List<TaskSource> selected,DunningDeliveryMail.MailResult result) {
        locks.acquire(billId);
        var ids=selected.stream().map(TaskSource::taskId).toList();
        var at=repository.now();
        for(var task:repository.tasks(billId))if(ids.contains(task.taskId())&&task.state().equals("SENDING"))
            change(task,result.state(),selected.get(0).messageId(),result.state().equals("SENT")?at:null,result.errorCode(),result.errorMessage());
        repository.finalizeRound(billId,eventKey);
    }
    @Transactional
    public void finalizeRound(String billId,String eventKey) {locks.acquire(billId);repository.finalizeRound(billId,eventKey);}
    @Transactional
    public void recoverInterrupted(String billId,String eventKey) {
        locks.acquire(billId);
        for(var task:repository.tasks(billId))if(task.eventKey().equals(eventKey)&&task.state().equals("SENDING"))
            change(task,"UNKNOWN",task.messageId(),null,"MAIL_ACCEPTANCE_UNKNOWN","중단된 발송입니다. 접수 여부를 확인해 주세요. 자동 재발송하지 않습니다.");
        repository.finalizeRound(billId,eventKey);
    }
    private void change(TaskSource task,String state,String messageId,java.time.LocalDateTime sentAt,String code,String message) {
        repository.transition(task,new TaskTransition(task.taskId(),task.state(),state,messageId,sentAt,code,message,repository.now()));
    }
    public static String normalized(String address) {return address==null?"":address.strip().toLowerCase(Locale.ROOT);}
}
