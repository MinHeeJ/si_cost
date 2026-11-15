package com.sicost.workforcerecommendations;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.candidates.RecommendationActionDtos;
import com.sicost.candidates.RecommendationActionRepository;
import com.sicost.profitability.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.workforcerecommendations.WorkforceRecommendationDtos.*;

@Service
public class WorkforceRecommendationService {
    private final WorkforceRecommendationRepository repository;
    private final AiWorkforceScoring scoring;
    private final AssignmentMonthlyService monthly;
    private final MonthlyCostService costs;
    private final CurrentUserProvider users;
    private final Clock clock;
    private final RecommendationActionRepository actions;
    public WorkforceRecommendationService(WorkforceRecommendationRepository repository,AiWorkforceScoring scoring,AssignmentMonthlyService monthly,MonthlyCostService costs,CurrentUserProvider users,Clock clock,RecommendationActionRepository actions){this.repository=repository;this.scoring=scoring;this.monthly=monthly;this.costs=costs;this.users=users;this.clock=clock;this.actions=actions;}
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public Data create(Condition input){
        String actor=users.currentUser().userId();repository.lock(input);
        var mapper=repository.mapper();Target target=mapper.target(input);
        if(target==null)throw new ApiException(404,"NOT_FOUND","추천 대상을 찾을 수 없습니다.");
        if(!"ING".equals(target.status())||"IN".equals(target.contractType()))throw new ApiException(409,"INVALID_STATE","진행 중인 기회 또는 내부가 아닌 프로젝트를 선택해 주세요.");
        if("OPPORTUNITY".equals(input.targetType())&&!mapper.needExists(input))throw WorkforceRecommendationInput.invalid("needSn");
        List<Code> codes=mapper.codes();
        if(codes.stream().noneMatch(c->"JOB".equals(c.groupId())&&input.jobCode().equals(c.code())))throw WorkforceRecommendationInput.invalid("jobCode");
        if(codes.stream().noneMatch(c->"GRADE".equals(c.groupId())&&input.requestedGradeCode().equals(c.code())))throw WorkforceRecommendationInput.invalid("requestedGradeCode");
        YearMonth start=YearMonth.parse(input.startMonth());if(start.isBefore(monthly.cashBase()))throw WorkforceRecommendationInput.invalid("startMonth");
        if(target.startDate()!=null&&start.isBefore(YearMonth.from(target.startDate())))start=YearMonth.from(target.startDate());
        if(target.endDate()!=null&&start.isAfter(YearMonth.from(target.endDate())))throw WorkforceRecommendationInput.invalid("startMonth");
        Condition condition=new Condition(input.targetType(),input.targetId(),input.jobCode(),start.toString(),input.requestedGradeCode(),input.needSn());
        LocalDate day=LocalDate.now(clock.withZone(TimeConfiguration.SEOUL));YearMonth current=YearMonth.from(day);
        List<BigDecimal> weights=scoring.weights(mapper.standards(AssignmentMonthlyService.db(current.toString())));
        List<Person> people=mapper.people();List<Leave> leaves=mapper.leaves();List<Tentative> tents=mapper.tentatives();
        Set<String> excluded=new HashSet<>();mapper.exclusions(condition).forEach(e->excluded.add(e.developerId()));
        List<AiWorkforceScoring.History> histories=mapper.assignments().stream().map(a->new AiWorkforceScoring.History(a,monthly.required(a.assignmentId()))).toList();
        Map<String,BigDecimal> mm=new HashMap<>();
        for(var history:histories){var source=history.source();if("CNCL".equals(source.status()))continue;
            for(var m:monthly.preview(source,monthly.mapper().histories(source.id()),history.assignment().projectStatus(),history.assignment().contractType()))if(condition.startMonth().equals(m.month()))mm.merge(source.developerId(),new BigDecimal(m.monthlyMm()),BigDecimal::add);}
        Map<String,AiWorkforceScoring.Labor> labor=new HashMap<>();
        for(var person:costs.getMonthlyCosts(new MonthlyCostInput.Query(current.getYear(),current.getMonthValue(),null)).items()){
            var source=person.laborSource();if(source!=null)labor.put(person.personId(),new AiWorkforceScoring.Labor(AssignmentMonthlyService.db(current.toString()),source.laborCost(),source.estimated()));}
        String sourceRevision=repository.revision(mapper.snapshot());
        var context=new AiWorkforceScoring.Context(condition,target,day,codes,mapper.skills(),mapper.areas(),histories);
        List<Diagnostic> diagnostics=new ArrayList<>();Map<String,Integer> reasonCounts=new TreeMap<>();List<Calculation> eligible=new ArrayList<>();
        for(Person person:people){List<String> reasons=new ArrayList<>();String id=person.developerId();
            if(!"Y".equals(person.useYn())||!"DRCT".equals(person.directClass()))reasons.add("활성 직접 인력 아님");
            if("LEAVE".equals(person.workStatus())||leaves.stream().anyMatch(l->id.equals(l.developerId())&&((!l.startDate().isAfter(day)&&(l.endDate()==null||!l.endDate().isBefore(day)))||(!l.startDate().isAfter(YearMonth.parse(condition.startMonth()).atEndOfMonth())&&(l.endDate()==null||!l.endDate().isBefore(YearMonth.parse(condition.startMonth()).atDay(1)))))))reasons.add("휴직");
            if(person.retireDate()!=null||"RETIRE".equals(person.workStatus()))reasons.add("퇴사 또는 퇴사 예정");
            if(person.hireDate()!=null&&person.hireDate().isAfter(start.atEndOfMonth()))reasons.add("시작월 이후 입사");
            if(mm.getOrDefault(id,BigDecimal.ZERO).compareTo(BigDecimal.ONE)>=0)reasons.add("실제 투입 MM 포화");
            if("DP".equals(target.contractType())&&Set.of("FL","OS").contains(person.employeeTypeCode()))reasons.add("파견 직원구분");
            if("OPPORTUNITY".equals(condition.targetType())&&tents.stream().anyMatch(t->id.equals(t.developerId())&&condition.targetId().equals(t.opportunityId())))reasons.add("현재 기회 가배정");
            if(excluded.contains(id))reasons.add("사용자 제외");
            if(!reasons.isEmpty()){diagnostics.add(new Diagnostic(id,List.copyOf(reasons)));reasons.forEach(r->reasonCounts.merge(r,1,Integer::sum));continue;}
            var cost=labor.getOrDefault(id,new AiWorkforceScoring.Labor(AssignmentMonthlyService.db(current.toString()),null,false));
            eligible.add(scoring.calculate(person,context,weights,cost,mapper.price(id,AssignmentMonthlyService.db(condition.startMonth()))));
        }
        Exclusions exclusions=new Exclusions(people.size(),diagnostics.size(),reasonCounts.entrySet().stream().map(e->new ReasonCount(e.getKey(),e.getValue())).toList(),List.copyOf(diagnostics),true);
        List<Calculation> top=eligible.stream().sorted(Comparator.comparing((Calculation c)->new BigDecimal(c.totalScore())).reversed()
            .thenComparing(c->c.detail().s().get(6),Comparator.reverseOrder()).thenComparing(c->c.person().managementNumber()).thenComparing(c->c.person().developerId())).limit(5).toList();
        if(top.isEmpty())return new Data(null,"AI",condition,null,null,null,List.copyOf(Collections.emptyList()),exclusions,"맞는 인력이 없습니다. 제외 사유를 확인해 주세요.",sourceRevision,List.copyOf(Collections.emptyList()));
        List<String> encoded=top.stream().map(c->repository.encode(c.detail(),c.reason())).toList();
        String id;
        try{id=mapper.nextId();}catch(org.springframework.dao.DataAccessException e){throw new ApiException(409,"ID_EXHAUSTED","추천 ID를 더 이상 발급할 수 없습니다.");}
        if(id==null||!id.matches("[1-9][0-9]{0,11}"))throw new ApiException(409,"ID_EXHAUSTED","추천 ID를 모두 사용했습니다.");
        LocalDateTime now=LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);List<Candidate> candidates=new ArrayList<>();
        for(int i=0;i<top.size();i++){Calculation c=top.get(i);Row row=new Row(id,i+1,"AI","OPPORTUNITY".equals(condition.targetType())?condition.targetId():null,condition.needSn(),"PROJECT".equals(condition.targetType())?condition.targetId():null,
            condition.jobCode(),AssignmentMonthlyService.db(condition.startMonth()),condition.requestedGradeCode(),c.person().developerId(),c.totalScore(),encoded.get(i),c.reason(),null,null,null,null,actor,now);
            var actionRow=new RecommendationActionDtos.Row(row.rcmdId(),row.rank(),row.recommendationType(),row.opportunityId(),row.needSn(),row.projectId(),row.jobCode(),row.startMonth(),row.developerId(),row.scoreDetail(),null,null,null,null,row.executedAt());
            var revisions=actions.current(actionRow);
            repository.insert(row,revisions.targetRevision(),revisions.candidateRevision(),revisions.expectedRevision());
            candidates.add(candidate(row,c.person().developer(),scoring.items(c.detail()),Map.of("targetRevision",revisions.targetRevision(),"candidateRevision",revisions.candidateRevision(),"expectedRevision",revisions.expectedRevision()),other(tents,row)));
        }
        repository.invalidate();return new Data(id,"AI",condition,null,WorkforceRecommendationRepository.time(now),actor,List.copyOf(candidates),exclusions,"추천은 참고용입니다. 배정은 담당자가 정합니다",repository.revision(candidates),List.copyOf(Collections.emptyList()));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Data get(String id){
        users.currentUser();List<Row> rows=repository.mapper().rows(id);if(rows.isEmpty())throw new ApiException(404,"NOT_FOUND","추천 회차를 찾을 수 없습니다.");
        Row first=rows.get(0);List<Candidate> candidates=new ArrayList<>();String source=null;
        for(Row row:rows){Map<String,String> metadata=repository.metadata(row);source=metadata.getOrDefault("sourceAssignmentId",source);
            var detail=repository.decode(row.scoreDetail());List<ScoreItem> items;
            if("AI".equals(row.recommendationType()))items=scoring.items(repository.ai(row.scoreDetail()));
            else{items=new ArrayList<>();List<String> keys=List.of("JOB","GRADE","GAP","CUST","PROB");for(int i=0;i<5;i++)items.add(new ScoreItem(keys.get(i),detail.path("b").get(i).decimalValue().toPlainString(),detail.path("w").get(i).decimalValue().toPlainString(),detail.path("s").get(i).decimalValue().toPlainString(),"당시 저장 근거"));}
            Person person=repository.mapper().person(row.developerId());
            if(person==null)throw new IllegalStateException("추천 인력 식별 정보를 찾을 수 없습니다.");
            candidates.add(candidate(row,person.developer(),items,metadata,List.copyOf(Collections.emptyList())));
        }
        Condition condition="AI".equals(first.recommendationType())?new Condition(first.projectId()==null?"OPPORTUNITY":"PROJECT",first.projectId()==null?first.opportunityId():first.projectId(),first.jobCode(),AssignmentMonthlyService.api(first.startMonth()),first.requestedGradeCode(),first.needSn()):null;
        return new Data(id,first.recommendationType(),condition,source,WorkforceRecommendationRepository.time(first.executedAt()),first.executedBy(),List.copyOf(candidates),new Exclusions(rows.size(),0,List.copyOf(Collections.emptyList()),List.copyOf(Collections.emptyList()),true),"저장된 추천 사본입니다.",repository.revision(rows),List.copyOf(Collections.emptyList()));
    }
    private List<OtherTentative> other(List<Tentative> tents,Row row){return tents.stream().filter(t->row.developerId().equals(t.developerId())&&!Objects.equals(row.opportunityId(),t.opportunityId())).map(t->new OtherTentative(t.tentativeAssignmentId(),t.opportunityId(),t.opportunityName(),t.needSn(),t.startMonth())).toList();}
    private Candidate candidate(Row row,Developer developer,List<ScoreItem> items,Map<String,String> metadata,List<OtherTentative> other){String revision=metadata.getOrDefault("expectedRevision",repository.revision(row));return new Candidate(row.rank(),developer,row.opportunityId(),row.needSn(),row.jobCode(),AssignmentMonthlyService.api(row.startMonth()),row.requestedGradeCode(),row.totalScore(),List.copyOf(items),repository.decode(row.scoreDetail()),row.reason(),other,repository.selection(row,metadata),revision,metadata.getOrDefault("targetRevision",revision),metadata.getOrDefault("candidateRevision",revision),repository.nextPosition(row));}
}
