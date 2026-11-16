package com.sicost.receivablesops;

import com.sicost.common.api.ApiException;
import com.sicost.common.notifications.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import static com.sicost.common.notifications.NotificationPublicationMapper.*;
import static com.sicost.receivablesops.DunningDeliveryDtos.*;

@Service
public class DunningDeliveryCommands {
    public record Submission(int status,CommandResult result) {}
    private final DunningDeliveryRepository repository;
    private final DunningDeliveryService service;
    private final NotificationPublisher publisher;
    private final DunningDeliveryLocks locks;
    private final DunningDeliveryWorker worker;
    private final DunningDeliveryMail mail;
    public DunningDeliveryCommands(DunningDeliveryRepository repository,DunningDeliveryService service,NotificationPublisher publisher,
        DunningDeliveryLocks locks,DunningDeliveryWorker worker,DunningDeliveryMail mail) {
        this.repository=repository;this.service=service;this.publisher=publisher;this.locks=locks;this.worker=worker;this.mail=mail;
    }
    @Transactional
    public Submission remind(String billId,ReminderInput input) {
        locks.acquire(billId);
        String key=NotificationEventKeys.manualDunning(billId,input.operationId());
        var existing=repository.rounds(billId).stream().filter(r->key.equals(r.eventKey())).findFirst().orElse(null);
        // A harmless duplicate is resolved before comparing a preview which our first command invalidated.
        if(existing!=null) {
            if(existing.terminal()&&existing.history()==null) {
                repository.finalizeRound(billId,key);
                existing=repository.rounds(billId).stream().filter(r->key.equals(r.eventKey())).findFirst().orElseThrow();
            }
            if(!existing.terminal())dispatch(billId,key);
            return new Submission(existing.terminal()?200:202,result(billId,input.operationId(),existing));
        }
        var preview=service.preview(billId);
        if(preview.inProgress())throw conflict("DELIVERY_IN_PROGRESS","같은 청구의 전달을 처리 중입니다.");
        service.requireExpected(input.expectedRevision(),preview.revision());
        if(!preview.canRemind())throw conflict("INVALID_BILL_STATE",String.join(" ",preview.unavailableReasons()));
        var round=reserve(billId,preview,key,true);
        dispatch(billId,key);
        return new Submission(202,result(billId,input.operationId(),round));
    }
    @Transactional
    public void automatic(String billId) {
        locks.acquire(billId);
        var bill=repository.required(billId);
        if(bill.cancelled()||bill.unpaidAmount().signum()<=0)return;
        var preview=service.preview(billId);
        if(preview.currentStep()==0||repository.highestStep(billId)>=preview.currentStep())return;
        if(preview.inProgress())return;
        if(preview.recipients().isEmpty())throw conflict("RECIPIENT_UNASSIGNED","알림 수신자 미지정");
        if(!preview.canRemind())return;
        reserve(billId,preview,NotificationEventKeys.automaticDunning(billId,preview.currentStep()),false);
    }
    private DeliveryRound reserve(String billId,DeliveryPreview preview,String key,boolean resend) {
        int serial=repository.nextSerial(billId);
        if(serial>99999)throw conflict("DUNNING_SEQUENCE_EXHAUSTED","알림 회차를 더 이상 예약할 수 없습니다.");
        String snapshot=String.join(", ",preview.recipients().stream().map(DeliveryRecipient::userName).limit(3).toList())+(preview.recipients().size()>3?" 외 "+(preview.recipients().size()-3)+"명":"");
        if(snapshot.codePointCount(0,snapshot.length())>300)throw conflict("RECIPIENT_SNAPSHOT_TOO_LONG","수신자 요약 길이를 확인해 주세요.");
        String content="청구 "+billId+" · "+preview.currentStep()+"단계 · 미수 "+preview.unpaidAmount()+"원";
        var event=new Event(key,"DUN",content,new Link("UI-13",billId),new Dunning(billId,preview.currentStep(),serial,resend));
        var recipients=preview.recipients().stream().map(r->new NotificationRecipients.Recipient(r.userId(),r.userName(),r.emailAddress(),r.qualificationCodes())).toList();
        if(publisher.publish(event,recipients).size()!=recipients.size())throw conflict("STALE_WRITE","수신자가 변경되었습니다. 미리보기를 다시 확인해 주세요.");
        var at=repository.now();String body=mail.body(preview);
        Map<String,String> messageIds=new HashMap<>();
        for(var recipient:preview.recipients()) {
            String taskId=UUID.randomUUID().toString();
            String address=recipient.emailMissing()?null:recipient.emailAddress().strip();
            String messageId=messageIds.computeIfAbsent(address==null?"missing:"+recipient.userId():address.toLowerCase(Locale.ROOT),a->"<"+UUID.randomUUID()+"@sicost.invalid>");
            String error=recipient.emailMissing()?"RECIPIENT_EMAIL_MISSING":!preview.mailConfigured()?"MAIL_NOT_CONFIGURED":null;
            String errorMessage=error==null?null:error.equals("RECIPIENT_EMAIL_MISSING")?"수신자 이메일이 없습니다.":"메일 설정이 없어 시스템 알림만 처리했습니다.";
            repository.reserve(new MailReservation(taskId,key,billId,serial,preview.currentStep(),resend,recipient.userId(),recipient.userName(),address,error==null?"PENDING":"FAILED",error,errorMessage,
                content,"청구 "+billId+" · "+preview.currentStep()+"단계 미수 알림",body,messageId,at));
        }
        repository.finalizeRound(billId,key);
        return repository.rounds(billId).stream().filter(r->key.equals(r.eventKey())).findFirst().orElseThrow();
    }
    private void dispatch(String billId,String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {worker.submit(billId,key);}
        });
    }
    private static CommandResult result(String billId,UUID operationId,DeliveryRound round) {
        return new CommandResult(billId,round.eventKey(),operationId,round.reservedSn(),true,round.terminal(),round.tasks(),round.history());
    }
    private static ApiException conflict(String code,String message) {return new ApiException(409,code,message);}
}
