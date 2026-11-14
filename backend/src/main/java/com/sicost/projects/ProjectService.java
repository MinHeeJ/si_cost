package com.sicost.projects;

import com.sicost.budget.ProjectBudgetCalculation;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.WriteCoordinator;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.projects.ProjectRegistrationService;
import com.sicost.common.validation.ProjectInputPolicy;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.projects.ProjectDtos.*;
import static com.sicost.projects.ProjectRules.*;

@Service
public class ProjectService implements org.springframework.context.ApplicationEventPublisherAware {
    private org.springframework.context.ApplicationEventPublisher events;
    @Override public void setApplicationEventPublisher(org.springframework.context.ApplicationEventPublisher publisher) { this.events=publisher; }
    private final ProjectRepository repository;
    private final WriteCoordinator writes;
    private final ProjectRegistrationService registration;
    private final CurrentUserProvider users;
    private final ChangeHistoryWriter audit;
    private final Clock clock;
    public ProjectService(ProjectRepository repository,WriteCoordinator writes,ProjectRegistrationService registration,
                          CurrentUserProvider users,ChangeHistoryWriter audit,Clock clock) {
        this.repository=repository;this.writes=writes;this.registration=registration;this.users=users;this.audit=audit;this.clock=clock;
    }
    private static List<String> keys(String key) { return List.of(key,"operating-standards:CASH_BASE_YM","operating-standards:TGT_MRGN_RT"); }
    @Transactional
    public SaveResult createProject(CreateProjectRequest input) {
        String actor=users.userId();
        String[] id=new String[1]; Project[] saved=new Project[1]; DefaultsRow[] standards=new DefaultsRow[1];
        LocalDateTime now=LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);
        var result=writes.create(keys(ProjectRegistrationService.CREATION_LOCK),()->repository.snapshot(id[0]),()-> {
            standards[0]=repository.defaults(month());
            ProjectInputPolicy.requireWritableYear(input.year(),cashBase(standards[0]),clock);
            if (!repository.activeType(input.contractType())) throw ProjectInputPolicy.invalid("contractType");
            if ("IN".equals(input.contractType())) internalFields(input.fields(),input.partId());
            else {
                requireCustomer(input.fields().customerId(),null);
                Reference part=input.partId()==null?null:repository.part(input.partId());
                if (part==null || !part.active()) throw ProjectInputPolicy.invalid("partId");
            }
            saved[0]=resolve(null,input.fields(),input.contractType(),input.initialStatus(),standards[0]);
            id[0]=ProjectRegistrationService.allocate(registration.reservedIds(Collections.emptyList()));
        },()-> {
            Project p=saved[0];
            registration.register(id[0],new ProjectRegistrationService.Project(p.name(),p.customerId(),p.contractType(),p.status(),
                p.startDate(),p.endDate(),input.partId(),p.workplace(),number(p.estimateAmount()),number(p.contractAmount()),
                number(p.budgetAmount()),p.budgetManual(),number(p.teamExpenseAmount()),number(p.projectExpenseAmount()),p.feature()),actor,now);
            audit.append(new ChangeHistoryWriter.Change("TB_PJ_PROJ",id[0],"NEW","등록",null,p.name(),null));
        });
        return result(repository.required(id[0]),result,standards[0]);
    }
    @Transactional
    public SaveResult putProject(String id,PutProjectRequest input) {
        ProjectInput.id(id);String actor=users.userId();
        Project[] before=new Project[1],after=new Project[1];DefaultsRow[] standards=new DefaultsRow[1];
        List<ProjectChange> changes=new ArrayList<>();PartMove[] move=new PartMove[1];
        LocalDateTime now=LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);
        var result=writes.write(keys("PROJECT:"+id),input.expectedRevision(),()->repository.snapshot(id),()-> {
            before[0]=repository.required(id);standards[0]=repository.defaults(month());
            ProjectInputPolicy.requireWritableYear(input.year(),cashBase(standards[0]),clock);
            if ("LOSE".equals(before[0].status())) throw new ApiException(409,"INVALID_PROJECT_STATUS","실주 프로젝트는 조회만 할 수 있습니다.");
            if ("IN".equals(before[0].contractType())) internalFields(input.fields(),null);
            else requireCustomer(input.fields().customerId(),before[0].customerId());
            after[0]=resolve(before[0],input.fields(),before[0].contractType(),before[0].status(),standards[0]);
            if (!before[0].startDate().equals(after[0].startDate()) || !before[0].endDate().equals(after[0].endDate()))
                move[0]=checkPeriod(before[0],after[0],standards[0],actor,now);
            Reference oldCustomer=before[0].customerId()==null?null:repository.customer(before[0].customerId());
            Reference newCustomer=after[0].customerId()==null?null:repository.customer(after[0].customerId());
            changes.addAll(changes(before[0],after[0],oldCustomer,newCustomer));
            if (move[0]!=null) changes.add(new ProjectChange("PART","영업 파트 시작월",apiMonth(move[0].oldMonth()),apiMonth(move[0].newMonth())));
            int greatest=repository.greatestHistorySn(id);
            if ((long)greatest+changes.size()>99999) ProjectInputPolicy.nextSerial(99999);
        },()->!changes.isEmpty(),()-> {
            if (events!=null && (!before[0].startDate().equals(after[0].startDate()) || !before[0].endDate().equals(after[0].endDate())))
                events.publishEvent(new com.sicost.common.projects.ProjectLifecycle.PeriodChanged(id,after[0].startDate(),after[0].endDate()));
            repository.update(new Mutation(after[0],actor,now));
            if (move[0]!=null) repository.moveInitialPart(move[0]);
            int sn=repository.greatestHistorySn(id);
            for (ProjectChange change:changes) {
                repository.insertHistory(new HistoryWrite(id,ProjectInputPolicy.nextSerial(sn++),change,actor,now));
                audit.append(new ChangeHistoryWriter.Change("TB_PJ_PROJ",id,"UPD",change.name(),change.before(),change.after(),null));
            }
        });
        return result(repository.required(id),result,standards[0]);
    }
    private Project resolve(Project old,Fields f,String type,String status,DefaultsRow defaults) {
        BigDecimal contract=f.contractAmount();
        if (contract==null) { if (!"IN".equals(type)) throw ProjectInputPolicy.invalid("contractAmount");contract=BigDecimal.ZERO; }
        var budget=ProjectBudgetCalculation.resolve(type,contract,old==null?null:new ProjectBudgetCalculation.Budget(number(old.budgetAmount()),old.budgetManual()),
            old==null?null:number(old.contractAmount()),f.budgetMode(),f.budgetAmount(),defaults.marginRate());
        String original=old==null?money(contract):old.originalContractAmount();
        if (old!=null && !money(contract).equals(old.contractAmount()))
            original=repository.hasBills(old.id())?Objects.toString(old.originalContractAmount(),old.contractAmount()):money(contract);
        return new Project(old==null?null:old.id(),f.name(),f.customerId(),type,savedStatus(status,f.endDate(),ProjectInputPolicy.today(clock)),
            f.startDate(),f.endDate(),f.workplace(),f.feature(),money(f.estimateAmount()),original,money(contract),money(budget.amount()),
            money(allowance(f.teamExpenseAmount(),defaults.teamExpense(),"teamExpenseAmount")),
            money(allowance(f.projectExpenseAmount(),defaults.projectExpense(),"projectExpenseAmount")),budget.manual(),
            old==null?null:old.lossDate(),old==null?null:old.lossReason(),old==null?null:old.lossMemo(),old==null?null:old.opportunityId());
    }
    private void requireCustomer(String id,String previous) {
        Reference customer=id==null?null:repository.customer(id);
        if (customer==null || (!Objects.equals(id,previous) && !customer.active())) throw ProjectInputPolicy.invalid("customerId");
    }
    private PartMove checkPeriod(Project old,Project next,DefaultsRow defaults,String actor,LocalDateTime now) {
        repository.lockClose();
        List<ProjectPart> parts=repository.parts(old.id(),month());
        String first=parts.isEmpty()?null:parts.get(0).effectiveMonth().replace("-","");
        var uses=repository.periodUses(new PeriodCheck(old.id(),next.startDate(),next.endDate(),first));
        if (!uses.isEmpty()) throw new ApiException(409,"PROJECT_PERIOD_IN_USE","사용 중인 투입·배분표·파트의 기간을 확인해 주세요.","startDate",Map.of("targets",uses));
        YearMonth start=YearMonth.from(old.startDate().isBefore(next.startDate())?old.startDate():next.startDate());
        YearMonth end=YearMonth.from(old.endDate().isAfter(next.endDate())?old.endDate():next.endDate());
        Set<String> protectedMonths=new HashSet<>(repository.protectedMonths(old.id(),dbMonth(start),dbMonth(end)));
        for (YearMonth m=start;!m.isAfter(end);m=m.plusMonths(1)) {
            if ((m.isBefore(cashBase(defaults)) || protectedMonths.contains(dbMonth(m)))
                && !intersection(old.startDate(),old.endDate(),m).equals(intersection(next.startDate(),next.endDate(),m)))
                throw new ApiException(409,"CLOSED_MONTH","마감·이관·고정 월의 수행 범위는 바꿀 수 없습니다.","startDate",Map.of("month",m.toString()));
        }
        String newMonth=dbMonth(YearMonth.from(next.startDate()));
        if (first!=null && !first.equals(newMonth)) {
            if (parts.stream().skip(1).anyMatch(p->p.effectiveMonth().replace("-","").equals(newMonth)))
                throw new ApiException(409,"PROJECT_PERIOD_IN_USE","최초 파트의 새 시작월이 기존 파트와 충돌합니다.","startDate",Map.of("month",apiMonth(newMonth),"target","영업 파트"));
            return new PartMove(old.id(),first,newMonth,actor,now);
        }
        return null;
    }
    private SaveResult result(Project project,WriteCoordinator.WriteResult result,DefaultsRow standards) {
        var defaults=defaults(standards,project.contractType(),number(project.contractAmount()),YearMonth.from(ProjectInputPolicy.today(clock)));
        var warnings=new ArrayList<>(defaults.warnings());warnings.add("최종 저장 시점의 서버 기준을 적용했습니다. 저장값과 기본값을 확인해 주세요.");
        return new SaveResult(project,result.revision(),result.changed(),List.copyOf(warnings),defaults);
    }
    private String month() { return dbMonth(YearMonth.from(ProjectInputPolicy.today(clock))); }
    private static String dbMonth(YearMonth month) { return month.format(DateTimeFormatter.ofPattern("uuuuMM")); }
    private static String apiMonth(String month) { return month.substring(0,4)+"-"+month.substring(4); }
}
