package com.sicost.progress;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.validation.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.progress.ProjectProgressDtos.*;
import static com.sicost.progress.ProjectProgressCalculation.*;

@Service
public class ProjectProgressService {
    private final ProjectProgressRepository repository;
    private final WriteCoordinator writes;
    private final ProjectProgressAggregateRevision revisions;
    private final ProjectProgressQueryService queries;
    private final ChangeHistoryWriter audit;
    private final CurrentUserProvider users;
    private final Clock clock;
    public ProjectProgressService(ProjectProgressRepository repository,WriteCoordinator writes,ProjectProgressAggregateRevision revisions,
        ProjectProgressQueryService queries,ChangeHistoryWriter audit,CurrentUserProvider users,Clock clock) {
        this.repository=repository;this.writes=writes;this.revisions=revisions;this.queries=queries;this.audit=audit;this.users=users;this.clock=clock;
    }
    @Transactional
    public MutationResult createProjectProgressSteps(String id,Write input) {
        List<StepRow> prepared=new ArrayList<>();String[] message=new String[1];
        var result=writes.write(List.of("PROJECT:"+id),input.expectedRevision(),()->revisions.snapshot(id),()-> {
            ProjectRow p=editable(id,input.year());
            if(!repository.steps(id).isEmpty())return;
            prepared.addAll(initialize(p,repository.templates()));
            if(prepared.isEmpty())message[0]="계약기간이 짧아 기본 단계 계획을 만들 수 없습니다";
        },()->!prepared.isEmpty(),()-> {
            String actor=users.userId();LocalDateTime now=now();
            for(var row:prepared) {
                repository.insertStep(new StepWrite(row,actor,now));
                appendStep(row,null,"NEW");
            }
        });
        return result(id,input.year(),result,message[0],null);
    }
    @Transactional
    public MutationResult saveProjectStepDates(String id,Dates input) {
        List<StepRow> before=new ArrayList<>(),after=new ArrayList<>();
        var result=writes.write(List.of("PROJECT:"+id),input.expectedRevision(),()->revisions.snapshot(id),()-> {
            ProjectRow p=editable(id,input.year());before.addAll(repository.steps(id));after.addAll(dates(p,before,input.steps()));
        },()->!before.equals(after),()-> {
            String actor=users.userId();LocalDateTime now=now();
            for(int i=0;i<before.size();i++)if(!before.get(i).equals(after.get(i))) {
                repository.updateDates(new StepWrite(after.get(i),actor,now));appendStep(after.get(i),before.get(i),"UPD");
            }
        });
        return result(id,input.year(),result,null,null);
    }
    @Transactional
    public MutationResult saveProjectStepCompletion(String id,int sn,Write input) {
        StepRow[] before=new StepRow[1],after=new StepRow[1];
        var result=writes.write(List.of("PROJECT:"+id),input.expectedRevision(),()->revisions.snapshot(id),()-> {
            editable(id,input.year());before[0]=repository.steps(id).stream().filter(r->r.stepSn()==sn).findFirst()
                .orElseThrow(()->new ApiException(404,"NOT_FOUND","단계를 찾을 수 없습니다."));
            StepRow row=before[0];LocalDate today=ProjectInputPolicy.today(clock);
            if(row.completedDate()==null && row.startDate().isAfter(today))throw new ApiException(409,"INVALID_PROJECT_STATUS","미래에 시작하는 단계는 완료할 수 없습니다.");
            after[0]=row.completedDate()!=null?row:withDates(row,row.startDate(),row.endDate(),today);
        },()->!before[0].equals(after[0]),()-> {
            repository.complete(new StepWrite(after[0],users.userId(),now()));appendStep(after[0],before[0],"UPD");
        });
        return result(id,input.year(),result,null,null);
    }
    @Transactional
    public MutationResult createProjectWeekMemo(String id,MemoInput input) {return saveMemo(id,null,input);}
    @Transactional
    public MutationResult putProjectWeekMemo(String id,int sn,MemoInput input) {return saveMemo(id,sn,input);}
    private MutationResult saveMemo(String id,Integer sn,MemoInput input) {
        MemoRow[] before=new MemoRow[1],after=new MemoRow[1];
        var result=writes.write(List.of("PROJECT:"+id),input.expectedRevision(),()->revisions.snapshot(id),()-> {
            ProjectRow p=editable(id,input.year());
            if(Set.of("MO","DP").contains(p.contractType())&&input.actualProvided())throw ProjectInputPolicy.invalid("actualProgress");
            if(input.thisWeek()==null && input.nextWeek()==null && input.request()==null && input.actualProgress()==null)throw ProjectInputPolicy.invalid("내용");
            var rows=repository.memos(id);int serial;
            if(sn==null) {
                int maximum=rows.stream().mapToInt(MemoRow::memoSn).max().orElse(0);
                if(maximum>=99999)throw new ApiException(409,"ID_EXHAUSTED","사용 가능한 메모 순번을 모두 사용했습니다.");
                serial=maximum+1;
            }
            else {before[0]=rows.stream().filter(r->r.memoSn()==sn).findFirst().orElseThrow(()->new ApiException(404,"NOT_FOUND","메모를 찾을 수 없습니다."));serial=sn;}
            var old=before[0];
            after[0]=new MemoRow(id,serial,old==null?ProjectInputPolicy.today(clock):old.writtenDate(),input.thisWeek(),input.nextWeek(),input.request(),input.actualProgress(),
                old==null?null:old.registeredBy(),old==null?null:old.registeredName(),old==null?null:old.registeredAt(),
                old==null?null:old.modifiedBy(),old==null?null:old.modifiedName(),old==null?null:old.modifiedAt());
        },()->!Objects.equals(before[0],after[0]),()-> {
            var write=new MemoWrite(after[0],users.userId(),now());
            if(sn==null)repository.insertMemo(write);else repository.updateMemo(write);
            var old=before[0];var row=after[0];String key=id+":"+row.memoSn(),type=sn==null?"NEW":"UPD";
            append("TB_PJ_WEEK_MEMO",key,type,"THIS_WEEK_CN",old==null?null:old.thisWeek(),row.thisWeek());
            append("TB_PJ_WEEK_MEMO",key,type,"NEXT_WEEK_CN",old==null?null:old.nextWeek(),row.nextWeek());
            append("TB_PJ_WEEK_MEMO",key,type,"REQ_CN",old==null?null:old.request(),row.request());
            append("TB_PJ_WEEK_MEMO",key,type,"ACTL_PRGRS_RT",old==null?null:old.actualProgress(),row.actualProgress());
        });
        return result(id,input.year(),result,null,after[0].memoSn());
    }
    private ProjectRow editable(String id,int year) {
        var p=repository.required(id,ProjectInputPolicy.today(clock));
        ProjectProgressPolicy.requireEditable(year,p.sourceStatus(),p.endDate(),clock);
        if(year<p.startDate().getYear() || year>p.endDate().getYear())throw ProjectInputPolicy.invalid("year");
        return p;
    }
    private void appendStep(StepRow row,StepRow old,String type) {
        String key=row.projectId()+":"+row.stepSn();
        if(old==null) {
            append("TB_PJ_STEP",key,type,"PROJ_STEP_CD",null,row.stepCode());
            append("TB_PJ_STEP",key,type,"STEP_WGHT_RT",null,row.weight().setScale(2).toPlainString());
        }
        append("TB_PJ_STEP",key,type,"PLAN_BGN_DT",old==null?null:old.startDate(),row.startDate());
        append("TB_PJ_STEP",key,type,"PLAN_END_DT",old==null?null:old.endDate(),row.endDate());
        append("TB_PJ_STEP",key,type,"CMPL_DT",old==null?null:old.completedDate(),row.completedDate());
    }
    private void append(String table,String key,String type,String field,Object before,Object after) {
        if(!Objects.equals(before,after))audit.append(new ChangeHistoryWriter.Change(table,key,type,field,Objects.toString(before,null),Objects.toString(after,null),null));
    }
    private MutationResult result(String id,int year,WriteCoordinator.WriteResult result,String message,Integer memoSn) {
        var data=queries.getProjectProgress(id,year);
        return new MutationResult(id,result.changed(),message!=null?message:result.changed()?"저장했습니다.":"바뀐 내용이 없습니다.",data.progressRevision(),data,
            memoSn==null?null:data.memos().stream().filter(m->m.memoSn()==memoSn).findFirst().orElseThrow(),null);
    }
    private LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);}
}
