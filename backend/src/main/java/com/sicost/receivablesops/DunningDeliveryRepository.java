package com.sicost.receivablesops;

import com.sicost.common.api.ApiException;
import com.sicost.common.history.ChangeHistoryWriter;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.receivablesops.DunningDeliveryDtos.*;

@Repository
public class DunningDeliveryRepository {
    private final DunningDeliveryMapper mapper;
    private final ChangeHistoryWriter audits;
    private final Clock clock;
    public DunningDeliveryRepository(DunningDeliveryMapper mapper, ChangeHistoryWriter audits, Clock clock) {
        this.mapper=mapper;this.audits=audits;this.clock=clock;
    }
    public BillSource required(String id) {
        var bill=mapper.bill(id);
        if(bill==null)throw new ApiException(404,"NOT_FOUND","청구를 찾을 수 없습니다.");
        return bill;
    }
    public void lock(String id) { if(mapper.lockBill(id)==null)throw new ApiException(404,"NOT_FOUND","청구를 찾을 수 없습니다."); }
    public List<TaskSource> tasks(String id) {return mapper.tasks(id);}
    public List<DeliveryWork> interrupted() {return mapper.interrupted();}
    public List<DeliveryWork> automaticPending() {return mapper.automaticPending();}
    public String snapshot(String id) {return mapper.snapshot(id);}
    public int nextSerial(String id) {return mapper.nextSerial(id);}
    public List<DeliveryRound> rounds(String id) {
        var tasks=mapper.tasks(id);var histories=mapper.histories(id);
        return mapper.rounds(id).stream().map(r->{
            var rows=tasks.stream().filter(t->t.reservedSn()==r.reservedSn()).toList();
            return new DeliveryRound(r.eventKey(),r.reservedSn(),r.step(),r.resend(),histories.stream().filter(h->h.sn()==r.reservedSn()).findFirst().orElse(null),
                rows.stream().map(TaskSource::view).toList(),rows.stream().allMatch(t->DunningDeliveryDtos.terminal(t.state())));
        }).toList();
    }
    public void reserve(MailReservation row) {
        requireOne(mapper.insertMail(row));
        var fields=new LinkedHashMap<String,Object>();
        fields.put("TASK_ID",row.taskId());fields.put("EVENT_KEY",row.eventKey());fields.put("RCVR_USER_ID",row.userId());fields.put("RCVR_NM",row.userName());
        fields.put("CHANNEL_CD","MAIL");fields.put("STATE_CD",row.state());fields.put("NOTI_TYPE_CD","DUN");fields.put("NOTI_CN",row.content());
        fields.put("LINK_SCRN_ID","UI-13");fields.put("LINK_KEY",row.billId());fields.put("MAIL_TO",row.emailAddress());fields.put("MAIL_SUBJECT",row.subject());
        fields.put("MAIL_BODY",row.taskId()+":MAIL_BODY");fields.put("MESSAGE_ID",row.messageId());fields.put("NOTI_ID",null);
        fields.put("BILL_ID",row.billId());fields.put("DUN_STEP",row.step());fields.put("DUN_SN",row.serial());fields.put("RESEND_YN","Y");
        fields.put("SENT_DTTM",null);fields.put("ERROR_CD",row.errorCode());fields.put("ERROR_CN",row.errorMessage());fields.put("REG_USER_ID","SYSTEM");fields.put("REG_DTTM",row.at());
        fields.forEach((field,value)->audit(row.taskId(),"NEW",field,null,value));
    }
    public void transition(TaskSource old, TaskTransition row) {
        requireOne(mapper.transition(row));
        audit(old.taskId(),"UPD","STATE_CD",old.state(),row.state());
        audit(old.taskId(),"UPD","MESSAGE_ID",old.messageId(),row.messageId());
        audit(old.taskId(),"UPD","SENT_DTTM",old.sentAt(),row.sentAt());
        audit(old.taskId(),"UPD","ERROR_CD",old.errorCode(),row.errorCode());
        audit(old.taskId(),"UPD","ERROR_CN",old.errorMessage(),row.errorMessage());
        // Include actual old MOD values in the same transaction, rather than deriving them from the request.
        audit(old.taskId(),"UPD","MOD_USER_ID",old.modifiedBy(),"SYSTEM");audit(old.taskId(),"UPD","MOD_DTTM",old.modifiedAt(),row.at());
    }
    public void finalizeRound(String billId, String eventKey) {
        lock(billId);
        var round=rounds(billId).stream().filter(r->r.eventKey().equals(eventKey)).findFirst().orElse(null);
        if(round==null||!round.terminal()||round.history()!=null)return;
        var successes=tasks(billId).stream().filter(t->eventKey.equals(t.eventKey())&&t.state().equals("SENT")).toList();
        if(successes.isEmpty())return;
        var names=successes.stream().collect(java.util.stream.Collectors.toMap(TaskSource::userId,TaskSource::userName,(a,b)->a,TreeMap::new));
        String description=String.join(", ",names.values().stream().limit(3).toList())+(names.size()>3?" 외 "+(names.size()-3)+"명":"");
        var channels=successes.stream().map(TaskSource::channelCode).distinct().toList();
        var last=successes.stream().map(TaskSource::sentAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElseThrow();
        var row=new HistoryInsert(billId,round.reservedSn(),round.step(),last.toLocalDate(),description,channels.size()==2?"BOTH":channels.get(0),round.resend()?"Y":"N",now());
        requireOne(mapper.insertHistory(row));
    }
    public LocalDateTime now() {return LocalDateTime.ofInstant(clock.instant(),ZoneId.of("Asia/Seoul"));}
    private void audit(String key,String type,String field,Object before,Object after) {
        if(type.equals("UPD")&&Objects.equals(before,after))return;
        audits.appendAsSystem(new ChangeHistoryWriter.Change("TB_SY_DELIVERY_TASK",key,type,field,before==null?null:before.toString(),after==null?null:after.toString(),null));
    }
    private static void requireOne(int count) {if(count!=1)throw new IllegalStateException("전달 증거를 저장하지 못했습니다.");}
}
