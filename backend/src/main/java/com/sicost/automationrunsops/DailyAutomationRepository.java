package com.sicost.automationrunsops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.concurrent.*;
import com.sicost.common.history.ChangeHistoryWriter;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static com.sicost.automationrunsops.DailyAutomationDtos.*;

@Repository
public class DailyAutomationRepository {
    private final DailyAutomationMapper mapper;
    private final MonthCloseBoundary boundary;
    private final SettlementSourceLocks locks;
    private final ChangeHistoryWriter history;
    private final Clock clock;
    private final TransactionTemplate transactions;
    public DailyAutomationRepository(DailyAutomationMapper mapper,MonthCloseBoundary boundary,SettlementSourceLocks locks,
                                     ChangeHistoryWriter history,Clock clock,PlatformTransactionManager manager) {
        this.mapper=mapper;this.boundary=boundary;this.locks=locks;this.history=history;this.clock=clock;
        transactions=new TransactionTemplate(manager);transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public Stored find(Key key) { return mapper.run(key); }
    public <T> T item(Collection<String> keys,Supplier<T> action) {
        return transactions.execute(status->{
            boundary.shared();locks.lock(keys);com.sicost.permission.PermissionTaskContext.checkpoint();
            if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void beforeCommit(boolean readOnly) {com.sicost.permission.PermissionTaskContext.checkpoint();}
                });
            T result=action.get();com.sicost.permission.PermissionTaskContext.checkpoint();return result;
        });
    }
    public Stored prepare(Key key) {
        return item(List.of("AUTOMATION:"+key.jobCode()+":"+key.runDate()),()->{
            Stored old=mapper.run(key);if(old!=null)return old;
            LocalDateTime at=now();
            int inserted=mapper.insertRun(new RunWrite(key,"RUNNING",0,at,null,null));
            if(inserted==1) {
                Map<String,Object> fields=new LinkedHashMap<>();fields.put("JOB_CD",key.jobCode());fields.put("RUN_DT",key.runDate());fields.put("STATE_CD","RUNNING");
                fields.put("LAST_STEP",0);fields.put("START_DTTM",at);fields.put("END_DTTM",null);fields.put("ERROR_CN",null);fields.put("REG_USER_ID","SYSTEM");fields.put("REG_DTTM",at);
                fields.forEach((field,value)->audit(key,"NEW",field,null,value));
            } else if(inserted!=0)throw new IllegalStateException("실행 기록을 저장하지 못했습니다.");
            return Objects.requireNonNull(mapper.run(key));
        });
    }
    public Stored update(Key key,String state,int lastStep,String error) {
        return item(List.of("AUTOMATION:"+key.jobCode()+":"+key.runDate()),()->{
            Stored old=Objects.requireNonNull(mapper.run(key));
            LocalDateTime ended=state.equals("RUNNING")?null:now();
            if(mapper.updateRun(new RunWrite(key,state,lastStep,now(),ended,error))!=1)throw new IllegalStateException("실행 상태를 저장하지 못했습니다.");
            audit(key,"UPD","STATE_CD",old.state(),state);audit(key,"UPD","LAST_STEP",old.lastStep(),lastStep);
            audit(key,"UPD","END_DTTM",old.endedAt(),ended);audit(key,"UPD","ERROR_CN",old.errorMessage(),error);
            return Objects.requireNonNull(mapper.run(key));
        });
    }
    private void audit(Key key,String type,String field,Object before,Object after) {
        if(type.equals("UPD")&&Objects.equals(before,after))return;
        history.appendAsSystem(new ChangeHistoryWriter.Change("TB_SY_AUTOMATION_RUN",key.jobCode()+":"+key.runDate(),type,field,
            before==null?null:before.toString(),after==null?null:after.toString(),null));
    }
    public LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL); }
    public void cleanup(LocalDate date) {
        item(List.of("notifications:cleanup"),()->{
            LocalDateTime cutoff=now().minusDays(30);
            List<String> ids=mapper.expiredNotificationIds(cutoff);
            for(String id:ids) {
                var row=mapper.notification(id);if(row==null)throw new IllegalStateException("정리 대상이 변경되었습니다.");
                Map<String,Object> fields=new LinkedHashMap<>();fields.put("NOTI_ID",row.id());fields.put("RCVR_USER_ID",row.userId());fields.put("NOTI_TYPE_CD",row.type());
                fields.put("NOTI_CN",row.content());fields.put("LINK_SCRN_ID",row.screenId());fields.put("LINK_KEY",row.linkKey());fields.put("SEND_MTHD_CD",row.method());
                fields.put("READ_YN",row.read());fields.put("READ_DTTM",row.readAt());fields.put("NOTI_DTTM",row.notifiedAt());fields.put("REG_USER_ID",row.regUserId());
                fields.put("REG_DTTM",row.registeredAt());fields.put("MOD_USER_ID",row.modUserId());fields.put("MOD_DTTM",row.modifiedAt());
                fields.forEach((field,value)->history.appendAsSystem(new ChangeHistoryWriter.Change("TB_SY_NOTI",id,"DEL",field,value==null?null:value.toString(),null,null)));
            }
            if(mapper.cleanup(cutoff)!=ids.size())throw new IllegalStateException("알림 정리 대상을 확인하지 못했습니다.");
            return null;
        });
    }
}
