package com.sicost.automationrunsops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import java.time.*;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import static com.sicost.automationrunsops.DailyAutomationDtos.*;

@Service
public class DailyAutomationService {
    private final DailyAutomationRepository repository;
    private final DailyAutomationSessionLocks locks;
    private final DailyAutomationStages stages;
    private final TaskExecutor executor;
    private final Clock clock;
    private final boolean manualRunEnabled;
    private com.sicost.authentication.AuthenticationTaskService authentication;
    private com.sicost.permission.PermissionService permissions;
    @org.springframework.beans.factory.annotation.Autowired
    public void setUserTasks(com.sicost.authentication.AuthenticationTaskService authentication,com.sicost.permission.PermissionService permissions) {this.authentication=authentication;this.permissions=permissions;}
    public DailyAutomationService(DailyAutomationRepository repository,DailyAutomationSessionLocks locks,DailyAutomationStages stages,
        @Qualifier("dailyAutomationExecutor") TaskExecutor executor,Clock clock,@Value("${AUTOMATION_MANUAL_RUN_ENABLED:false}") boolean manualRunEnabled) {
        this.repository=repository;this.locks=locks;this.stages=stages;this.executor=executor;this.clock=clock;this.manualRunEnabled=manualRunEnabled;
    }
    public boolean manualRunEnabled() { return manualRunEnabled; }
    public void requireManualEnabled() { if(!manualRunEnabled)throw new ApiException(404,"NOT_FOUND","요청한 자료를 찾을 수 없습니다."); }
    public CommandResult runToday(String jobCode) {
        requireManualEnabled();validateJob(jobCode);
        if(!(org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() instanceof org.springframework.web.context.request.ServletRequestAttributes request))throw com.sicost.permission.PermissionService.denied();
        var proof=authentication.capture(request.getRequest());requireManualPermission(jobCode,proof);
        return com.sicost.permission.PermissionTaskContext.user(()->requireManualPermission(jobCode,proof),()->submit(jobCode,proof));
    }
    private void requireManualPermission(String job,com.sicost.authentication.AuthenticationTaskService.Proof proof) {
        requireManualEnabled();authentication.checkpoint(proof);permissions.requireRole("ADMIN");permissions.requireAll();
        if(job.equals("RECEIVABLE_DUNNING"))permissions.require("UI-13","UPD");
        else {permissions.require("UI-02","UPD");permissions.require("UI-01","UPD");permissions.require("UI-12","UPD");permissions.require("UI-05","INQ");permissions.require("UI-21","INQ");}
    }
    public static void validateJob(String jobCode) {
        if(!Set.of("DAILY_OPERATIONS","RECEIVABLE_DUNNING").contains(jobCode))
            throw new ApiException(400,"VALIDATION_ERROR","정해진 자동 처리 작업만 실행할 수 있습니다.","jobCode",null);
    }
    public CommandResult scheduled(String jobCode) {
        validateJob(jobCode);
        return com.sicost.permission.PermissionTaskContext.system(com.sicost.permission.PermissionTaskContext.Command.valueOf(jobCode),()->submit(jobCode));
    }
    private CommandResult submit(String jobCode) {
        return submit(jobCode,null);
    }
    private CommandResult submit(String jobCode,com.sicost.authentication.AuthenticationTaskService.Proof proof) {
        Key key=new Key(jobCode,LocalDate.now(clock.withZone(TimeConfiguration.SEOUL)));
        Stored run=repository.prepare(key);
        if(run.state().equals("SUCCEEDED"))return new CommandResult(run.view(),false,true);
        var lease=locks.tryAcquire(key);
        if(lease==null) {
            Stored current=repository.find(key);boolean completed=current.state().equals("SUCCEEDED");
            return new CommandResult(current.view(),!completed,completed);
        }
        try {
            run=repository.find(key);
            if(run.state().equals("SUCCEEDED")){lease.close();return new CommandResult(run.view(),false,true);}
            Stored accepted=repository.update(key,"RUNNING",run.lastStep(),null);
            executor.execute(()->{
                if(proof==null)com.sicost.permission.PermissionTaskContext.system(com.sicost.permission.PermissionTaskContext.Command.valueOf(key.jobCode()),()->{execute(key,lease);return null;});
                else try {authentication.run(proof,()->com.sicost.permission.PermissionTaskContext.user(()->requireManualPermission(key.jobCode(),proof),()->{execute(key,lease);return null;}));}
                catch(RuntimeException rejected) {lease.close();com.sicost.permission.PermissionTaskContext.system(com.sicost.permission.PermissionTaskContext.Command.valueOf(key.jobCode()),()->{repository.update(key,"FAILED",repository.find(key).lastStep(),"작업 접수 후 인증 또는 권한이 변경되었습니다.");return null;});}
            });
            return new CommandResult(accepted.view(),true,false);
        } catch(RuntimeException error) { lease.close();throw error; }
    }
    private void execute(Key key,DailyAutomationSessionLocks.Lease lease) {
        try(lease) {
            int checkpoint=repository.find(key).lastStep();
            int count=key.jobCode().equals("DAILY_OPERATIONS")?7:4;
            for(int stage=checkpoint+1;stage<=count;stage++) {
                try {
                    com.sicost.permission.PermissionTaskContext.checkpoint();
                    stages.execute(key,stage);
                    repository.update(key,stage==count?"SUCCEEDED":"RUNNING",stage,null);
                } catch(RuntimeException error) {
                    String detail=error instanceof DailyAutomationStages.ItemFailure item?item.publicMessage():"단계 "+stage+" / JOB="+key.jobCode()+" / 처리 또는 필수 감사 저장 실패";
                    repository.update(key,"FAILED",stage-1,detail);
                    return;
                }
            }
        }
    }
}
