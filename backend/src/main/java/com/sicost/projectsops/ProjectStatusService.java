package com.sicost.projectsops;

import com.sicost.budget.ProjectBudgetCalculation;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.common.validation.ProjectInputPolicy;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.projectsops.ProjectStatusDtos.*;

@Service
public class ProjectStatusService implements org.springframework.context.ApplicationEventPublisherAware {
    private org.springframework.context.ApplicationEventPublisher events;
    @Override public void setApplicationEventPublisher(org.springframework.context.ApplicationEventPublisher publisher) { this.events=publisher; }
    private final ProjectStatusRepository repository;
    private final WriteCoordinator writes;
    private final RevisionService revisions;
    private final CurrentUserProvider users;
    private final ChangeHistoryWriter history;
    private final Clock clock;
    private final AssignmentMonthlyService monthly;
    public ProjectStatusService(ProjectStatusRepository repository,WriteCoordinator writes,RevisionService revisions,
                                CurrentUserProvider users,ChangeHistoryWriter history,Clock clock,AssignmentMonthlyService monthly) {
        this.monthly=monthly;this.repository=repository;this.writes=writes;this.revisions=revisions;this.users=users;this.history=history;this.clock=clock;
    }
    private LocalDate today() {return ProjectInputPolicy.today(clock);}
    private String db(YearMonth month) {return DatabaseMonthCodec.toDatabase(month.toString());}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Defaults defaults(BigDecimal amount,String type) {
        users.currentUser();YearMonth month=YearMonth.from(today());
        BigDecimal rate=ProjectBudgetCalculation.validRate(repository.rate(db(month)));
        BigDecimal budget="IN".equals(type)?null:ProjectBudgetCalculation.preview(amount,rate);
        BigDecimal team=ProjectBudgetCalculation.allowanceDefault(repository.mapper().allowance("TEAM_FEE"));
        BigDecimal expense=ProjectBudgetCalculation.allowanceDefault(repository.mapper().allowance("PEXP_FEE"));
        List<String> warnings=new ArrayList<>();
        if(budget==null&&!"IN".equals(type))warnings.add("유효한 목표이익률이 없습니다. 예산을 수기로 입력해 주세요.");
        if(team==null)warnings.add("유효한 팀비 기본금액이 없습니다. 직접 입력해 주세요.");
        if(expense==null)warnings.add("유효한 경비 기본금액이 없습니다. 직접 입력해 주세요.");
        return new Defaults(amount.toPlainString(),type,ProjectInputPolicy.amountString(budget),ProjectInputPolicy.amountString(rate),
            ProjectInputPolicy.amountString(team),ProjectInputPolicy.amountString(expense),month.toString(),List.copyOf(warnings));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Options options(String purpose,String q) {
        users.currentUser();LocalDate day=today();
        return new Options(repository.mapper().options(new Selection(purpose,q,day,db(YearMonth.from(day)))));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Preview preview(String id) {
        users.currentUser();ProjectStatusInput.id(id);LocalDate day=today();requirePre(repository.required(id),day);requireOpen(day);
        return previewValue(id,day);
    }
    private Preview previewValue(String id,LocalDate day) {
        var assignments=repository.mapper().assignments(id);
        var current=assignments.stream().filter(a->"ACT".equals(a.status())&&!a.startDate().isAfter(day)&&!a.endDate().isBefore(day)).toList();
        var future=assignments.stream().filter(a->"ACT".equals(a.status())&&a.startDate().isAfter(day)).toList();
        return new Preview(day,current.stream().map(Assignment::developerId).distinct().count(),future.stream().map(Assignment::developerId).distinct().count(),
            current.size(),future.size(),current,future,token(id,day),revisions.revision(repository.snapshot(id)));
    }
    private String token(String id,LocalDate day) {
        return revisions.revision(new PreviewSnapshot(id,day,repository.mapper().assignmentSnapshot(id)));
    }
    @Transactional
    public Result progress(String id,Progress input) {
        ProjectStatusInput.id(id);ProjectInputPolicy.expectedRevision(input.expectedRevision());String actor=users.userId();
        var result=writes.write(List.of("PROJECT:"+id,"operating-standards:CASH_BASE_YM"),input.expectedRevision(),()->repository.snapshot(id),
            ()->{requirePre(repository.required(id),today());ProjectInputPolicy.requireWritableYear(input.year(),repository.cashBase(),clock);},
            ()->true,()->{
                changeProject(id,"ING",null,null,actor,today());
                if (events!=null) events.publishEvent(new com.sicost.common.projects.ProjectLifecycle.Registered(id));
                // The assignments keep their rows; now that the project is in progress their monthly sales are computed.
                Project now=repository.required(id);String status=ProjectAssignmentMonths.effectiveStatus(now,today());
                for(AssignmentRow row:repository.mapper().assignmentRows(id))
                    if("ACT".equals(row.status()))monthly.synchronize(monthly.required(row.id()),status,now.contractType());
            });
        return new Result(repository.required(id),result.revision(),result.changed());
    }
    @Transactional
    public Result loss(String id,Loss input) {
        ProjectStatusInput.id(id);ProjectInputPolicy.expectedRevision(input.expectedRevision());String actor=users.userId();
        // Discover membership before taking the globally sorted locks; recheck it under PROJECT.
        requirePre(repository.required(id),today());
        List<String> discovered=repository.mapper().assignmentRows(id).stream().map(AssignmentRow::id).toList();
        List<String> keys=new ArrayList<>();keys.add("PROJECT:"+id);keys.add("operating-standards:CASH_BASE_YM");
        discovered.forEach(a->keys.add("ASGN:"+a));
        LocalDate[] day=new LocalDate[1];String[] reason=new String[1];boolean[] initialSnapshot={true};
        var result=writes.write(keys,input.expectedRevision(),()->{
                if(initialSnapshot[0]){requirePre(repository.required(id),today());initialSnapshot[0]=false;}
                return repository.snapshot(id);
            },()->{
                repository.mapper().lockClose();day[0]=today();requirePre(repository.required(id),day[0]);
                ProjectInputPolicy.requireWritableYear(input.year(),repository.cashBase(),clock);requireOpen(day[0]);
                if(!discovered.equals(repository.mapper().assignmentRows(id).stream().map(AssignmentRow::id).toList()))throw stale();
                revisions.requireExpected(input.previewToken(),token(id,day[0]));
                reason[0]=repository.mapper().reason(input.lossReason());
                if(reason[0]==null)throw ProjectInputPolicy.invalid("lossReason");
                ProjectInputPolicy.optionalText(input.lossMemo(),500,"lossMemo");
            },()->true,()->{
                for(AssignmentRow row:repository.mapper().assignmentRows(id)) {
                    if(!"ACT".equals(row.status())||row.endDate().isBefore(day[0]))continue;
                    changeAssignment(id,row,day[0],reason[0],actor);
                }
                for(String month:repository.mapper().costMonths(id)) {
                    if(!repository.protectedMonth(YearMonth.parse(DatabaseMonthCodec.toApi(month))))
                        repository.mapper().classifyCost(new CostWrite(id,month,actor,now()));
                }
                changeProject(id,"LOSE",input.lossReason(),input.lossMemo(),actor,day[0]);
            });
        return new Result(repository.required(id),result.revision(),result.changed());
    }
    private void changeProject(String id,String status,String reason,String memo,String actor,LocalDate day) {
        Project before=repository.required(id);int serial=ProjectInputPolicy.nextSerial(repository.serial(id));LocalDateTime now=now();
        repository.one(repository.mapper().updateProject(new ProjectWrite(id,status,day,reason,memo,actor,now)));
        repository.one(repository.mapper().insertEvent(new Event(id,serial,before.status(),status,day,actor,now)));
        history.append(new ChangeHistoryWriter.Change("TB_PJ_PROJ",id,"UPD","상태",before.status(),status,null));
    }
    private void changeAssignment(String projectId,AssignmentRow row,LocalDate day,String reason,String actor) {
        boolean future=row.startDate().isAfter(day);LocalDateTime now=now();
        int serial=ProjectInputPolicy.nextSerial(repository.mapper().assignmentSerial(row.id()));
        boolean cross=false;
        for(YearMonth m=YearMonth.from(row.startDate());m.isBefore(YearMonth.from(day))&&!m.isAfter(YearMonth.from(row.endDate()));m=m.plusMonths(1))
            if(repository.protectedMonth(m)){cross=true;break;}
        repository.one(repository.mapper().updateAssignment(new AssignmentWrite(row.id(),projectId,day,future,actor,now)));
        repository.one(repository.mapper().insertAssignmentEvent(new AssignmentEvent(row.id(),serial,future?"CNCL":"EARLY",day,
            future?row.status():row.endDate().toString(),future?"CNCL":day.toString(),ProjectAssignmentMonths.eventMemo(reason),cross?"Y":"N",actor,now)));
        List<ValueHistory> values=repository.mapper().valueHistory(row.id());
        Map<String,String> existing=new TreeMap<>();repository.mapper().months(row.id()).forEach(m->existing.put(m.month(),m.fixed()));
        Set<String> months=new TreeSet<>(existing.keySet());
        if(!future)for(YearMonth m=YearMonth.from(row.startDate());!m.isAfter(YearMonth.from(day));m=m.plusMonths(1))months.add(db(m));
        for(String key:months) {
            YearMonth month=YearMonth.parse(DatabaseMonthCodec.toApi(key));
            if("Y".equals(existing.get(key))||repository.protectedMonth(month))continue;
            int days=future?0:ProjectAssignmentMonths.days(month,row.startDate(),day);
            if(days==0){if(existing.containsKey(key))repository.mapper().deleteMonth(row.id(),key);continue;}
            BigDecimal mm=ProjectAssignmentMonths.value(values,month,true,row.contractMm());
            BigDecimal price=ProjectAssignmentMonths.value(values,month,false,row.price());
            repository.one(repository.mapper().saveMonth(new MonthWrite(row.id(),projectId,row.developerId(),key,days,ProjectAssignmentMonths.mm(mm,days,month),price,actor,now)));
        }
    }
    private void requirePre(Project project,LocalDate day) {
        if(!"PRE".equals(ProjectAssignmentMonths.effectiveStatus(project,day)))
            throw new ApiException(409,"INVALID_PROJECT_STATUS","유효한 수주예정 프로젝트만 전환할 수 있습니다.");
    }
    private void requireOpen(LocalDate day) {
        YearMonth month=YearMonth.from(day);
        if(repository.protectedMonth(month))throw new ApiException(409,"CLOSED_MONTH","마감 또는 이관된 월에는 실주 처리할 수 없습니다.",null,Map.of("month",month.toString()));
    }
    private ApiException stale() {return new ApiException(409,"STALE_WRITE","투입 배정이 변경되었습니다. 영향을 다시 미리보기해 주세요.");}
    private LocalDateTime now() {return LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);}
}
