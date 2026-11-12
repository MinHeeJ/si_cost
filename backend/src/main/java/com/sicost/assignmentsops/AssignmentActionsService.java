package com.sicost.assignmentsops;

import com.sicost.common.api.*;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.assignments.AssignmentMonthlyService.*;
import com.sicost.common.assignments.AssignmentMonthlyService.Month;
import com.sicost.common.concurrent.*;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.persistence.MasterIdService;
import com.sicost.common.validation.*;
import com.sicost.projectsops.ProjectAssignmentMonths;
import com.sicost.projectsops.ProjectStatusDtos.ValueHistory;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import static com.sicost.assignmentsops.AssignmentActionsDtos.*;
import static com.sicost.common.validation.ProjectInputPolicy.*;
import static com.sicost.common.assignments.AssignmentMonthlyService.*;

@Service
public class AssignmentActionsService {
    private final AssignmentActionsRepository repository;
    private final AssignmentMonthlyService months;
    private final AggregateLockMapper locks;
    private final RevisionService revisions;
    private final CurrentUserProvider users;
    private final ChangeHistoryWriter audit;
    private final MasterIdService ids;
    private final CommitInvalidation invalidation;
    private final Clock clock;
    private final TransactionTemplate itemTransaction;
    public AssignmentActionsService(AssignmentActionsRepository repository,AssignmentMonthlyService months,AggregateLockMapper locks,
        RevisionService revisions,CurrentUserProvider users,ChangeHistoryWriter audit,MasterIdService ids,CommitInvalidation invalidation,
        Clock clock,PlatformTransactionManager manager) {
        this.repository=repository;this.months=months;this.locks=locks;this.revisions=revisions;this.users=users;this.audit=audit;this.ids=ids;this.invalidation=invalidation;this.clock=clock;
        itemTransaction=new TransactionTemplate(manager);itemTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    private AssignmentActionsMapper mapper() {return repository.mapper();}
    private LocalDate today() {return ProjectInputPolicy.today(clock);}
    private LocalDateTime now() {return LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul")));}
    private String effective(Project p) {return "LOSE".equals(p.status())?"LOSE":p.endDate().isBefore(today())?"END":p.status();}
    private void writable(int year) {requireWritableYear(year,months.cashBase(),clock);}
    private void present(Source row,int year) {
        if(!mapper().matchingIds(new Filter(year,null,null,null,null,true)).contains(row.id()))throw invalid("year");
    }
    private void lock(Collection<String> keys) {keys.stream().distinct().sorted().forEach(locks::lock);months.mapper().lockClose();}
    private List<String> keys(Source row) {return List.of("ASGN:"+row.id(),"PROJECT:"+row.projectId(),"DEV:"+row.developerId(),"operating-standards:CASH_BASE_YM","operating-standards:MM_OVER");}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Month> getMonths(String id,int year,int page,int size) {
        users.currentUser();ProjectInputPolicy.year(year,months.cashBase(),clock);Source source=repository.required(id);present(source,year);
        var history=months.mapper().histories(id);var rows=months.mapper().months(id).stream().filter(m->m.month().startsWith(Integer.toString(year))).map(m->months.view(source,m,history)).toList();
        return page(rows,page,size);
    }
    private <T> Page<T> page(List<T> values,int page,int size) {long start=(long)(page-1)*size;return new Page<>(values.stream().skip(start).limit(size).toList(),values.size(),page,size);}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Candidate> candidates(String projectId,String developerId,int page,int size) {
        users.currentUser();repository.project(projectId);repository.developer(developerId);
        var values=mapper().candidates(new CandidateQuery(projectId,developerId)).stream().map(c->{
            Source row=repository.required(c.lastAssignmentId());BigDecimal price=ProjectAssignmentMonths.value(months.mapper().histories(row.id()),YearMonth.from(row.endDate()),false,row.price());
            return new Candidate(c.developerId(),c.developerName(),c.managementNo(),c.lastAssignmentId(),c.withdrawalDate(),amount(price));
        }).toList();return page(values,page,size);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Selection selection(SelectRequest request) {
        users.currentUser();writable(request.year());List<String> matches=mapper().matchingIds(request.filters()).stream().filter(id->matchesWarning(id,request.filters())).toList();
        if(!matches.containsAll(request.ids())||!matches.containsAll(request.excludedIds()))throw invalid("ids");
        List<String> selected="PAGE".equals(request.scope())?request.ids():matches;List<Item> result=new ArrayList<>();Set<String> protection=new TreeSet<>();
        for(String id:selected)if(!request.excludedIds().contains(id)){result.add(new Item(id,repository.revision(id)));protection.addAll(months.protectedMonths(repository.required(id)));}
        return new Selection(List.copyOf(result),result.size(),List.copyOf(protection));
    }
    private boolean matchesWarning(String id,Filter f) {
        if(f.warning()==null)return true;Source row=repository.required(id);Project project=repository.project(row.projectId());
        if("UNCONTRACTED".equals(f.warning()))return "PRE".equals(effective(project));
        return warnings(row,project,storedValues(row)).stream().anyMatch(w->w.code().equals(f.warning())
            && (w.months().isEmpty()||w.months().stream().anyMatch(m->m.startsWith(f.year()+"-"))));
    }
    private List<Month> storedValues(Source row) {var h=months.mapper().histories(row.id());return months.mapper().months(row.id()).stream().map(m->months.view(row,m,h)).toList();}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,noRollbackFor=ApiException.class)
    public Preview preview(PreviewRequest request) {
        users.currentUser();Source source;Project project;List<ValueHistory> history;
        if(request.assignment()!=null){source=prepare(request.assignment());project=repository.project(source.projectId());history=List.of(input(source,1));}
        else {
            Source before=repository.required(request.id());writable(request.change().year());present(before,request.change().year());revisions.requireExpected(request.change().expectedRevision(),repository.revision(before.id()));
            Plan plan=plan(before,request.change());source=plan.source();project=repository.project(source.projectId());history=new ArrayList<>(months.mapper().histories(source.id()));
            if(history.isEmpty())history.add(input(before,1));if(plan.changed())history.add(new ValueHistory(mapper().serial(source.id())+2,request.change().type(),plan.date(),request.change().newContractMm(),request.change().newUnitPrice(),"N",null));
        }
        List<Month> calculated=months.preview(source,history,effective(project),project.contractType());List<String> affected=range(source.startDate(),source.endDate());
        return new Preview(warnings(source,project,calculated),affected,months.protectedMonths(source),calculated);
    }
    public Source prepare(Create create) {
        writable(create.year());Project project=repository.project(create.projectId());Developer developer=repository.developer(create.developerId());
        if(!Set.of("ING","PRE").contains(effective(project)))throw conflict("INVALID_PROJECT_STATUS","진행 또는 수주예정 프로젝트만 신규 배정할 수 있습니다.");
        if(!"Y".equals(developer.useYn())||!"DRCT".equals(developer.directClass())||developer.retireDate()!=null&&!developer.retireDate().isAfter(today()))throw invalid("developerId");
        if(!mapper().active(new CodeQuery("JOB",create.jobCode())))throw invalid("jobCode");
        if(!mapper().active(new CodeQuery("INPUT_TYPE",create.inputType())))throw invalid("inputType");
        period(create.startDate(),create.endDate(),project,developer);
        if(create.startDate().getYear()>create.year()||create.endDate().getYear()<create.year())throw invalid("year");
        BigDecimal price=null;String supplied=optionalText(create.billingUnitPrice(),100,"billingUnitPrice");
        if("SUB".equals(create.inputType())) {
            if(create.developerId().equals(create.contractDeveloperId()))throw invalid("contractDeveloperId");
            if(mapper().candidates(new CandidateQuery(create.projectId(),create.developerId())).stream().noneMatch(c->c.developerId().equals(create.contractDeveloperId())))throw invalid("contractDeveloperId");
        }
        if("FREE".equals(create.inputType())){if(supplied!=null)throw invalid("billingUnitPrice");price=BigDecimal.ZERO;}
        else if(Set.of("MO","DP").contains(project.contractType())) {
            if(supplied!=null)price=AssignmentInputPolicy.paidUnitPrice(supplied);
            else if("SUB".equals(create.inputType())) {
                var candidate=mapper().candidates(new CandidateQuery(create.projectId(),create.developerId())).stream().filter(c->c.developerId().equals(create.contractDeveloperId())).findFirst().orElseThrow(()->invalid("contractDeveloperId"));
                Source previous=repository.required(candidate.lastAssignmentId());price=ProjectAssignmentMonths.value(months.mapper().histories(previous.id()),YearMonth.from(previous.endDate()),false,previous.price());
            } else price=mapper().price(new PriceQuery(create.developerId(),db(YearMonth.from(create.startDate()).toString())));
            if(price==null||price.signum()<=0)throw invalid("billingUnitPrice");
        } else if(supplied!=null)throw invalid("billingUnitPrice");
        Source row=new Source("",create.projectId(),create.developerId(),create.jobCode(),create.startDate(),create.endDate(),create.contractMm(),price,create.inputType(),create.contractDeveloperId(),null,0,"N","ACT");
        if(mapper().duplicate(row))throw conflict("DUPLICATE_ASSIGNMENT","동일한 배정이 있습니다.");return row;
    }
    private void period(LocalDate start,LocalDate end,Project p,Developer d) {
        dateRange(start,end);if(start.isBefore(p.startDate())||end.isAfter(p.endDate()))throw invalid("endDate");
        if(d.joinDate()!=null&&start.isBefore(d.joinDate()))throw invalid("startDate");if(d.retireDate()!=null&&end.isAfter(d.retireDate()))throw invalid("endDate");
    }
    private ValueHistory input(Source row,int serial) {return new ValueHistory(serial,"INPUT",row.startDate(),row.contractMm(),row.price(),"N",null);}
    private List<Warning> warnings(Source source,Project p,List<Month> values) {
        List<Warning> result=new ArrayList<>();Developer d=repository.developer(source.developerId());List<String> protectedMonths=months.protectedMonths(source);
        if(!protectedMonths.isEmpty())result.add(warning("CLOSE_CROSS","마감한 달 값은 유지하고 미마감 달부터 반영합니다. 과거 차액은 전월 조정으로 입력하세요.",protectedMonths,source));
        if("PRE".equals(effective(p)))result.add(warning("UNCONTRACTED","미계약 수주예정 프로젝트입니다.",List.of(),source));
        if("DP".equals(p.contractType())&&Set.of("FL","OS").contains(d.employeeType()))result.add(warning("EMPLOYEE_TYPE","파견 프로젝트의 FL·OS 인력입니다.",List.of(),source));
        if(mapper().leave(new LeaveQuery(d.id(),source.startDate(),source.endDate())))result.add(warning("LEAVE","휴직 기간이 겹칩니다.",List.of(),source));
        String part=mapper().projectPart(new PartQuery(p.id(),db(YearMonth.from(source.startDate()).toString())));
        if(!"IN".equals(p.contractType())&&part!=null&&!part.equals(d.partId()))result.add(warning("PART_DIFFERENCE","프로젝트 매출과 사람 소속파트 정산은 별개입니다. 소속파트는 자동 변경하지 않습니다.",List.of(),source));
        for(Month month:values){BigDecimal limit=mapper().mmLimit(db(month.month()));BigDecimal total=mapper().totalMm(new TotalQuery(d.id(),db(month.month()),source.id().isEmpty()?null:source.id())).add(new BigDecimal(month.monthlyMm()));
            if(limit!=null&&total.compareTo(limit)>0)result.add(warning("MM_OVER",month.month()+" 전프로젝트 월MM "+total.toPlainString()+" > "+limit.toPlainString(),List.of(month.month()),source));}
        return List.copyOf(result);
    }
    private Warning warning(String code,String message,List<String> keys,Source row) {return new Warning(code,message,keys,row.id().isEmpty()?List.of():List.of(row.id()));}
    private record Plan(Source source,LocalDate date,String before,String after,boolean retroactive,boolean changed) {}
    private Plan plan(Source row,Change c) {
        Project p=repository.project(row.projectId());Developer d=repository.developer(row.developerId());
        if(!"ACT".equals(row.status())||"LOSE".equals(effective(p)))throw conflict("INVALID_ASSIGNMENT_STATUS","취소 배정 또는 실주 프로젝트는 일반 변경할 수 없습니다.");
        LocalDate apply=c.applyDate();LocalDate end=row.endDate();BigDecimal price=row.price(),mm=row.contractMm();String kind=row.inputType(),possibility=row.extensionPossibility(),early=row.earlyWithdrawal();int count=row.extensionCount();String before,after;boolean changed=true;
        switch(c.type()) {
            case "EXT" -> {if(!c.newEndDate().isAfter(end))throw invalid("newEndDate");if(count>=999)throw conflict("EXTENSION_LIMIT","연장 횟수는 999를 초과할 수 없습니다.");apply=end.plusDays(1);before=end.toString();end=c.newEndDate();after=end.toString();count++;period(row.startDate(),end,p,d);}
            case "EARLY" -> {end=c.withdrawalDate();if(end.isBefore(row.startDate())||end.isAfter(row.endDate()))throw invalid("withdrawalDate");apply=end;before=row.endDate().toString();after=end.toString();changed=!end.equals(row.endDate())||!"Y".equals(early);early="Y";}
            case "UPRC","RATE" -> {
                if(c.applyMonth().isBefore(YearMonth.from(row.startDate()))||c.applyMonth().isAfter(YearMonth.from(row.endDate())))throw invalid("applyMonth");apply=c.applyMonth().atDay(1);
                boolean rate="RATE".equals(c.type());BigDecimal old=ProjectAssignmentMonths.value(months.mapper().histories(row.id()),c.applyMonth(),rate,rate?row.contractMm():row.price());BigDecimal next=rate?c.newContractMm():c.newUnitPrice();
                if(!rate&&(!Set.of("MO","DP").contains(p.contractType())||"FREE".equals(kind)))throw invalid("newUnitPrice");
                if(equal(old,next))throw same();before=rate?old.setScale(2).toPlainString():amount(old);after=rate?next.setScale(2).toPlainString():amount(next);
            }
            case "EXTP" -> {before=possibility;after=c.extensionPossibility();if(Objects.equals(before,after))throw same();possibility=after;}
            case "KIND" -> {if(!Set.of("NEW","ADD").contains(kind))throw new ApiException(400,"INVALID_INPUT","SUB/FREE 전환은 철수 또는 취소 후 새로 배정해 주세요.","inputType",null);before=kind;after=c.inputType();if(before.equals(after))throw same();kind=after;}
            default -> throw invalid("type");
        }
        if(c.applyMonth()==null&&apply.isBefore(row.startDate()))throw invalid("applyDate");
        boolean retro=c.applyMonth()!=null?c.applyMonth().isBefore(YearMonth.from(today())):apply.isBefore(today());
        Source next=row.changed(end,mm,price,kind,possibility,count,early,row.status());List<String> protection=protection(row,next);
        reason(c.reasonCode(),"EARLY".equals(c.type())||retro||!protection.isEmpty());
        return new Plan(next,apply,before,after,retro,changed);
    }
    private List<String> protection(Source before,Source after) {Set<String> keys=new TreeSet<>(months.protectedMonths(before));keys.addAll(months.protectedMonths(after));return List.copyOf(keys);}
    private void reason(String code,boolean required) {if(code==null){if(required)throw invalid("reasonCode");}else if(!mapper().active(new CodeQuery("ASGN_CHG_RSN",code)))throw invalid("reasonCode");}
    @Transactional
    public Save change(String id,Change c) {
        users.currentUser();Source discovery=repository.required(id);lock(keys(discovery));writable(c.year());Source before=repository.required(id);present(before,c.year());revisions.requireExpected(c.expectedRevision(),repository.revision(id));
        Plan plan=plan(before,c);if(plan.changed())persistChange(before,c,plan);return saved(id,c.year(),plan.changed());
    }
    private void persistChange(Source before,Change c,Plan plan) {
        ensureInput(before);String actor=users.userId();LocalDateTime time=now();List<String> protection=protection(before,plan.source());
        repository.one(mapper().update(new Write(plan.source(),actor,time)));
        repository.one(mapper().event(new Event(before.id(),serial(before.id()),c.type(),plan.date(),plan.before(),plan.after(),c.reasonCode(),c.memo(),c.newUnitPrice(),c.newContractMm(),plan.retroactive()?"Y":"N",protection.isEmpty()?"N":"Y",actor,time)));
        audit.append(new ChangeHistoryWriter.Change("TB_PJ_ASGN",before.id(),"UPD",c.type(),plan.before(),plan.after(),c.reasonCode()));
        if("EARLY".equals(c.type())&&Objects.equals(plan.before(),plan.after()))audit.append(new ChangeHistoryWriter.Change("TB_PJ_ASGN",before.id(),"UPD","EARLY_WTHDR_YN",before.earlyWithdrawal(),"Y",c.reasonCode()));
        Project p=repository.project(before.projectId());months.synchronize(plan.source(),effective(p),p.contractType());invalidate(before);
    }
    private void ensureInput(Source before) {
        if(!months.mapper().histories(before.id()).isEmpty())return;
        repository.one(mapper().event(new Event(before.id(),serial(before.id()),"INPUT",before.startDate(),null,summary(before),null,null,before.price(),before.contractMm(),"N","N",users.userId(),now())));
    }
    private int serial(String id) {int sn=mapper().serial(id);if(sn>=99999)throw conflict("HISTORY_EXHAUSTED","변경 이력 번호를 모두 사용했습니다.");return sn+1;}
    private String summary(Source s) {return s.projectId()+"/"+s.developerId()+"/"+s.jobCode()+"/"+s.startDate()+"/"+s.endDate();}
    private void invalidate(Source row) {keys(row).forEach(invalidation::afterCommit);}
    public Save saved(String id,int year,boolean changed) {Source row=repository.required(id);Project p=repository.project(row.projectId());var warnings=warnings(row,p,storedValues(row));var protection=months.protectedMonths(row);return new Save(view(row,year,warnings,protection),changed,warnings,protection);}
    private Assignment view(Source row,int year,List<Warning> warnings,List<String> protection) {
        Labels names=mapper().labels(row.id());BigDecimal yearly=months.mapper().months(row.id()).stream().filter(m->m.month().startsWith(Integer.toString(year))).map(StoredMonth::mm).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new Assignment(row.id(),row.projectId(),names.projectName(),row.developerId(),names.developerName(),names.managementNo(),row.jobCode(),names.jobName(),row.startDate(),row.endDate(),row.contractMm().setScale(2).toPlainString(),amount(row.price()),row.inputType(),row.contractDeveloperId(),row.extensionPossibility(),row.extensionCount(),row.earlyWithdrawal(),row.status(),yearly.setScale(2).toPlainString(),repository.revision(row.id()),warnings,protection);
    }
    public BulkResult bulk(Bulk request) {
        users.currentUser();writable(request.year());List<BulkItem> results=new ArrayList<>();
        for(Item item:request.items()) {
            try {results.add(itemTransaction.execute(status->bulkItem(request,item)));}
            catch(ApiException e){results.add(new BulkItem(item.id(),"rejected",null,new ApiError(false,Map.of(),e.code(),e.getMessage(),e.field(),e.details(),UUID.randomUUID().toString()),List.of()));}
            catch(RuntimeException e){results.add(new BulkItem(item.id(),"rejected",null,new ApiError(false,Map.of(),"INTERNAL_ERROR","자료 처리 중 오류가 발생했습니다.",null,null,UUID.randomUUID().toString()),List.of()));}
        }
        return new BulkResult(results.size(),(int)results.stream().filter(r->r.outcome().equals("applied")).count(),(int)results.stream().filter(r->r.outcome().equals("noChange")).count(),(int)results.stream().filter(r->r.outcome().equals("rejected")).count(),List.copyOf(results));
    }
    private BulkItem bulkItem(Bulk request,Item item) {
        Source discovered=repository.required(item.id());lock(keys(discovered));writable(request.year());Source row=repository.required(item.id());present(row,request.year());revisions.requireExpected(item.expectedRevision(),repository.revision(row.id()));
        var protection=months.protectedMonths(row);boolean cancellation="CNCL".equals(request.type());
        boolean noChange=cancellation?"CNCL".equals(row.status()):Objects.equals(row.extensionPossibility(),request.extensionPossibility());
        if(noChange)return new BulkItem(row.id(),"noChange",repository.revision(row.id()),null,protection);
        if(!"ACT".equals(row.status())||!cancellation&&"LOSE".equals(effective(repository.project(row.projectId()))))throw conflict("INVALID_ASSIGNMENT_STATUS","이 배정은 일반 변경할 수 없습니다.");
        boolean retro=cancellation&&row.startDate().isBefore(today());reason(request.reasonCode(),retro||!protection.isEmpty());ensureInput(row);
        Source next=row.changed(row.endDate(),row.contractMm(),row.price(),row.inputType(),cancellation?row.extensionPossibility():request.extensionPossibility(),row.extensionCount(),row.earlyWithdrawal(),cancellation?"CNCL":row.status());
        repository.one(mapper().update(new Write(next,users.userId(),now())));
        repository.one(mapper().event(new Event(row.id(),serial(row.id()),request.type(),today(),cancellation?row.startDate()+"/"+row.endDate():row.extensionPossibility(),cancellation?"CNCL":request.extensionPossibility(),request.reasonCode(),request.memo(),null,null,retro?"Y":"N",protection.isEmpty()?"N":"Y",users.userId(),now())));
        audit.append(new ChangeHistoryWriter.Change("TB_PJ_ASGN",row.id(),"UPD",request.type(),cancellation?"ACT":row.extensionPossibility(),cancellation?"CNCL":request.extensionPossibility(),request.reasonCode()));
        Project p=repository.project(row.projectId());months.synchronize(next,effective(p),p.contractType());invalidate(row);
        return new BulkItem(row.id(),"applied",repository.revision(row.id()),null,protection);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,noRollbackFor=ApiException.class)
    public SubPreview substitutionPreview(Create c) {users.currentUser();Source source=prepare(c);return subPreview(c,source);}
    private SubPreview subPreview(Create c,Source source) {
        List<String> candidates=mapper().overlaps(new Overlap(c.projectId(),c.contractDeveloperId(),c.startDate()));Set<String> protection=new TreeSet<>(months.protectedMonths(source));
        List<Item> items=candidates.stream().map(id->{protection.addAll(months.protectedMonths(repository.required(id)));return new Item(id,repository.revision(id));}).toList();
        Project p=repository.project(source.projectId());List<Warning> warnings=warnings(source,p,months.preview(source,List.of(input(source,1)),effective(p),p.contractType()));
        String notice="TK".equals(p.contractType())&&mapper().turnkey(new Overlap(p.id(),c.contractDeveloperId(),c.startDate()))?"턴키 배분표는 다시 옮겨 올려 주셔야 합니다. 그 전까지는 배분 불일치 경고가 뜹니다.":null;
        String token=revisions.revision(Arrays.asList(c,items,mapper().substitutionContext(c.projectId()),repository.developer(c.developerId()),repository.developer(c.contractDeveloperId()),today()));
        return new SubPreview(token,c.startDate().minusDays(1),items,warnings,List.copyOf(protection),notice);
    }
    @Transactional
    public SubResult substitute(Substitute request) {
        users.currentUser();Create c=request.assignment();List<String> discovered=mapper().overlaps(new Overlap(c.projectId(),c.contractDeveloperId(),c.startDate()));
        List<String> keys=new ArrayList<>(List.of("PROJECT:"+c.projectId(),"DEV:"+c.developerId(),"DEV:"+c.contractDeveloperId(),"operating-standards:CASH_BASE_YM","operating-standards:MM_OVER"));discovered.forEach(id->keys.add("ASGN:"+id));lock(keys);
        Source source=prepare(c);SubPreview preview=subPreview(c,source);revisions.requireExpected(request.previewToken(),preview.previewToken());
        List<String> withdrawn=new ArrayList<>();
        for(Withdrawal w:request.withdrawals()) {
            if(!preview.candidates().stream().anyMatch(i->i.id().equals(w.assignmentId()))||!preview.withdrawalDate().equals(w.withdrawalDate()))throw invalid("withdrawals");
            Source before=repository.required(w.assignmentId());revisions.requireExpected(w.expectedRevision(),repository.revision(before.id()));
            Change change=new Change(c.year(),w.expectedRevision(),"EARLY",null,null,null,w.withdrawalDate(),null,null,null,null,w.reasonCode(),w.memo());
            Plan plan=plan(before,change);if(plan.changed())persistChange(before,change,plan);withdrawn.add(before.id());
        }
        String id=ids.nextAssignmentId();Source created=new Source(id,source.projectId(),source.developerId(),source.jobCode(),source.startDate(),source.endDate(),source.contractMm(),source.price(),source.inputType(),source.contractDeveloperId(),null,0,"N","ACT");
        repository.one(mapper().insert(new Write(created,users.userId(),now())));ensureInput(created);audit.append(new ChangeHistoryWriter.Change("TB_PJ_ASGN",id,"NEW",null,null,summary(created),null));Project p=repository.project(c.projectId());months.synchronize(created,effective(p),p.contractType());invalidate(created);
        Save saved=saved(id,c.year(),true);return new SubResult(saved.assignment(),List.copyOf(withdrawn),true,saved.warnings(),preview.protectedMonths(),preview.turnkeyNotice());
    }
    private ApiException same() {return new ApiException(400,"SAME_VALUE","현재 적용값과 같습니다.");}
    private ApiException conflict(String code,String message) {return new ApiException(409,code,message);}
}
