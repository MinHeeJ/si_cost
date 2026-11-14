package com.sicost.progress;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.projects.ProjectLifecycle;
import com.sicost.common.validation.ProjectInputPolicy;
import java.time.*;
import java.util.*;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import static com.sicost.progress.ProjectProgressDtos.*;
import static com.sicost.progress.ProjectProgressCalculation.*;

@Component
public class ProjectProgressLifecycle {
    private final ProjectProgressRepository repository;
    private final ChangeHistoryWriter history;
    private final CurrentUserProvider users;
    private final Clock clock;
    public ProjectProgressLifecycle(ProjectProgressRepository repository,ChangeHistoryWriter history,CurrentUserProvider users,Clock clock) {
        this.repository=repository;this.history=history;this.users=users;this.clock=clock;
    }
    @EventListener
    @Transactional(propagation=Propagation.MANDATORY)
    public void registered(ProjectLifecycle.Registered event) {
        LocalDate today=ProjectInputPolicy.today(clock);var p=repository.required(event.projectId(),today);
        if(!Set.of("ING","PRE").contains(p.sourceStatus()) || p.endDate().isBefore(today) || !repository.steps(p.projectId()).isEmpty())return;
        var prepared=initialize(p,repository.templates());String actor=users.userId();LocalDateTime now=now();
        for(var r:prepared) {
            repository.insertStep(new StepWrite(r,actor,now));
            audit(r,"PROJ_STEP_CD",null,r.stepCode(),"NEW");audit(r,"STEP_WGHT_RT",null,r.weight().setScale(2).toPlainString(),"NEW");
            audit(r,"PLAN_BGN_DT",null,r.startDate(),"NEW");audit(r,"PLAN_END_DT",null,r.endDate(),"NEW");
        }
    }
    @EventListener
    @Transactional(propagation=Propagation.MANDATORY)
    public void periodChanged(ProjectLifecycle.PeriodChanged event) {
        var p=repository.required(event.projectId(),ProjectInputPolicy.today(clock));var before=repository.steps(p.projectId());
        if(before.isEmpty())return;
        List<StepRow> after=new ArrayList<>(before);
        var first=before.get(0);var last=before.get(before.size()-1);
        after.set(0,withDates(first,event.startDate(),first.endDate(),first.completedDate()));
        var updatedLast=after.get(after.size()-1);
        after.set(after.size()-1,withDates(updatedLast,updatedLast.startDate(),event.endDate(),updatedLast.completedDate()));
        var next=new ProjectRow(p.projectId(),p.projectName(),p.contractType(),p.sourceStatus(),event.startDate(),event.endDate(),p.budget(),p.registeredAt(),p.partId(),p.partName(),p.hq());
        if(!valid(next,after) || first.completedDate()!=null&&!event.startDate().equals(first.startDate())
            || last.completedDate()!=null&&!event.endDate().equals(last.endDate()))
            throw new ApiException(409,"STEP_PERIOD_CONFLICT","변경한 계약기간과 단계 계획이 충돌합니다. 미완료 중간 종료일을 먼저 확인해 주세요.","steps",Map.of("projectId",p.projectId(),"stepNumbers",before.stream().map(StepRow::stepSn).toList()));
        String actor=users.userId();LocalDateTime now=now();
        for(int i=0;i<before.size();i++)if(!before.get(i).equals(after.get(i))) {
            var old=before.get(i);var row=after.get(i);repository.updateDates(new StepWrite(row,actor,now));
            audit(row,"PLAN_BGN_DT",old.startDate(),row.startDate(),"UPD");audit(row,"PLAN_END_DT",old.endDate(),row.endDate(),"UPD");
        }
    }
    private void audit(StepRow row,String field,Object before,Object after,String type) {
        if(!Objects.equals(before,after))history.append(new ChangeHistoryWriter.Change("TB_PJ_STEP",row.projectId()+":"+row.stepSn(),type,field,Objects.toString(before,null),Objects.toString(after,null),null));
    }
    private LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);}
}
