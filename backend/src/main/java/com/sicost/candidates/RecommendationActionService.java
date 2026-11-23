package com.sicost.candidates;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sicost.apiops.*;
import com.sicost.assignmentcandidatesops.AssignmentCandidateRepository;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.context.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.candidates.RecommendationActionDtos.*;

@Service
public class RecommendationActionService {
    private final RecommendationActionRepository repository;
    private final RecommendationActionMapper mapper;
    private final TentativeSelectionService tentative;
    private final TentativeSelectionMapper tentatives;
    private final AssignmentCandidateRepository opportunities;
    private final AssignmentMonthlyService monthly;
    private final CurrentUserProvider users;
    private final Clock clock;
    private final ObjectMapper json;
    public RecommendationActionService(RecommendationActionRepository repository,RecommendationActionMapper mapper,TentativeSelectionService tentative,TentativeSelectionMapper tentatives,AssignmentCandidateRepository opportunities,AssignmentMonthlyService monthly,CurrentUserProvider users,Clock clock,ObjectMapper json){this.repository=repository;this.mapper=mapper;this.tentative=tentative;this.tentatives=tentatives;this.opportunities=opportunities;this.monthly=monthly;this.users=users;this.clock=clock;this.json=json;}
    @Transactional
    public Result exclude(Key key,Exclusion input){
        String actor=users.currentUser().userId();Row row=repository.lock(key,List.of());
        if(!"AI".equals(row.type()))throw RecommendationActionInput.invalid("rcmdId");
        Map<String,String> payload=Map.of("reasonCode",input.reasonCode(),"expectedRevision",input.expectedRevision());
        if(repository.replay(row,"EXCL",payload))return repository.result(row,false,repository.values(key),"이미 제외한 후보입니다.");
        if(!mapper.activeReason(input.reasonCode()))throw RecommendationActionInput.invalid("reasonCode");
        repository.requireCurrent(row,new Revisions(input.expectedRevision(),null,null));requireOpen(row);repository.requireDomain(row);
        repository.storeOriginal(row);Choice choice=new Choice(key,"EXCL",input.reasonCode(),actor,now());
        repository.select(row,choice,payload,Map.of());
        return repository.completed(row,choice,Map.of(),"같은 대상·적용 직무의 추천에서 제외했습니다.");
    }
    @Transactional
    public Result assignTentatively(Key key,Tentative input){
        String actor=users.currentUser().userId();Row before=repository.required(key);
        if(before.opportunityId()==null)throw RecommendationActionInput.invalid("rcmdId");
        if("NEXT".equals(before.type())&&!input.needSn().equals(before.needSn()))throw RecommendationActionInput.invalid("needSn");
        Need beforeNeed=mapper.need(new Slot(before.opportunityId(),input.needSn()));
        Set<String> keys=new TreeSet<>();
        if(before.actionCode()==null&&beforeNeed!=null){YearMonth start=start(before,beforeNeed);keys.add("PAY:"+AssignmentMonthlyService.db(start.toString()));}
        Row row=repository.lock(key,keys);
        Map<String,String> payload=new TreeMap<>();payload.put("needSn",input.needSn());payload.put("expectedRevision",input.expectedRevision());payload.put("targetRevision",input.targetRevision());payload.put("candidateRevision",input.candidateRevision());payload.put("confirmedWarnings",warningsJson(input.confirmedWarnings()));
        if(repository.replay(row,"TENT",payload))return repository.result(row,false,repository.values(key),"이미 저장한 가배정입니다.");
        requireOpen(row);repository.requireDomain(row);
        Need need=mapper.need(new Slot(row.opportunityId(),input.needSn()));if(need==null)throw state("선택한 필요 인력 행이 삭제되었습니다. 다시 조회해 주세요.");
        if(!"ING".equals(need.status()))throw state("진행 중인 기회만 가배정할 수 있습니다.");
        YearMonth start=start(row,need);if(!keys.contains("PAY:"+AssignmentMonthlyService.db(start.toString()))||!Objects.equals(beforeNeed,need))throw RecommendationActionRepository.stale();
        YearMonth needStart=YearMonth.parse(AssignmentMonthlyService.api(need.startMonth())),end=needStart.plusMonths(need.months()-1L);
        if(start.isAfter(end)||start.isBefore(monthly.cashBase()))throw state("가배정 시작월이 자리 기간 또는 운영 시작월을 벗어났습니다.");
        Developer person=mapper.developer(row.developerId());
        if(person==null||!"Y".equals(person.useYn())||!"DRCT".equals(person.directClass())||!Set.of("FT","CT","FL","OS").contains(person.employeeType())
            ||"DP".equals(need.contractType())&&Set.of("FL","OS").contains(person.employeeType())
            ||person.joinDate()!=null&&person.joinDate().isAfter(start.atEndOfMonth())||person.retireDate()!=null&&person.retireDate().isBefore(start.atDay(1))
            ||mapper.leave(new RecommendationActionDtos.Period(row.developerId(),start.atDay(1),start.atEndOfMonth())))throw state("인력의 입퇴사·휴직·계약 자격을 확인해 주세요.");
        var active=tentatives.active(row.developerId());if(active.size()>1)throw state("활성 가배정 충돌을 확인해 주세요.");
        if("NEXT".equals(row.type()))requireNext(row,active.isEmpty());
        boolean same=!active.isEmpty()&&active.get(0).opportunityId().equals(row.opportunityId())&&String.valueOf(active.get(0).needSn()).equals(input.needSn());
        var common=new TentativeSelectionInput.Create(row.opportunityId(),Integer.parseInt(input.needSn()),row.developerId(),start,"NEXT".equals(row.type())?"UI-05":"UI-30",opportunities.revision(row.opportunityId()),active.isEmpty()?null:opportunities.revision(active.get(0).opportunityId()),true);
        if(!same&&tentatives.count(common)>=need.count())throw new ApiException(409,"SLOT_FULL","선택한 자리의 정원이 찼습니다. 다시 조회해 주세요.","needSn",null);
        repository.requireCurrent(row,new Revisions(input.expectedRevision(),input.targetRevision(),input.candidateRevision()));
        List<Warning> warnings=new ArrayList<>();
        if(!same&&!active.isEmpty())warnings.add(new Warning("TENTATIVE_MOVE","다른 기회·자리의 가배정을 해제하고 현재 선택 자리로 이동합니다."));
        if(!row.jobCode().equals(need.jobCode())||!Objects.equals(person.jobCode(),need.jobCode()))warnings.add(new Warning("JOB_MISMATCH","인력의 주직무 또는 추천 적용 직무와 선택 자리 직무가 다릅니다. 추천 적용 직무와 점수는 변경하지 않습니다."));
        BigDecimal mm=BigDecimal.ZERO;
        for(String id:tentatives.assignmentIds(row.developerId())){
            var source=monthly.required(id);
            for(var month:monthly.preview(source,monthly.mapper().histories(id),"ING","MO"))if(start.toString().equals(month.month()))mm=mm.add(new BigDecimal(month.monthlyMm()));
        }
        if(mm.compareTo(BigDecimal.ONE)>=0)warnings.add(new Warning("MM_OVER","시작월 확정 투입 MM이 1 이상입니다. 실제 배정은 변경하지 않습니다."));
        Set<String> required=new TreeSet<>();warnings.forEach(w->required.add(w.code()));
        if(!required.equals(new TreeSet<>(input.confirmedWarnings())))throw new ApiException(409,"INVALID_STATE","최신 경고를 확인한 뒤 다시 가배정해 주세요.","confirmedWarnings",warnings.isEmpty()?null:Map.of("workforceRecommendationWarnings",warnings));
        repository.storeOriginal(row);
        var saved=tentative.create(common);
        Map<String,String> connection=new TreeMap<>();connection.put("tentativeAssignmentId",saved.tentativeId());connection.put("finalOpportunityId",need.opportunityId());connection.put("finalNeedSn",need.needSn());
        if(!same&&!active.isEmpty())connection.put("releasedTentativeAssignmentId",active.get(0).id());
        Choice choice=new Choice(key,"TENT",null,actor,now());repository.select(row,choice,payload,connection);
        boolean moved=connection.containsKey("releasedTentativeAssignmentId");
        var names=mapper.messageNames(new MessageContext(row.developerId(),need.jobCode(),moved?active.get(0).opportunityId():null));
        if(names==null)throw new IllegalStateException("가배정 인력·직무 이름을 확인하지 못했습니다.");
        String message=names.developerName()+"님을 「"+need.name()+"」 "+need.needSn()+"번 자리("+names.jobName()+")에 가배정했습니다.";
        if(moved)message+=" (이전 가배정 「"+names.previousOpportunityName()+"」은 풀렸습니다)";
        return repository.completed(row,choice,connection,message);
    }
    private YearMonth start(Row row,Need need){
        YearMonth result=YearMonth.parse(AssignmentMonthlyService.api(row.startMonth()));
        if("NEXT".equals(row.type())){Withdrawal source=withdrawal(row);result=YearMonth.from(source.endDate()).plusMonths(1);}
        else {LocalDate last=mapper.progressingEnd(row.developerId(),today());if(last!=null)result=max(result,YearMonth.from(last).plusMonths(1));}
        return max(result,YearMonth.parse(AssignmentMonthlyService.api(need.startMonth())));
    }
    private Withdrawal withdrawal(Row row){
        try{String id=json.readTree(row.scoreDetail()).path("e").path("aid").textValue();Withdrawal source=id==null?null:mapper.withdrawal(id);if(source==null)throw state("철수 원천 배정이 없습니다.");return source;}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw state("추천 원천을 확인할 수 없습니다.");}
    }
    private void requireNext(Row row,boolean isNew){
        Withdrawal source=withdrawal(row);LocalDate today=today();
        if(!"ACT".equals(source.status())||"LOSE".equals(source.projectStatus())||source.endDate().isBefore(today)||source.endDate().getYear()<today.getYear()||mapper.hasConfirmedNext(row.developerId(),source.endDate())||mapper.hasConvertedNext(row.developerId()))throw state("철수 완료·과거연도·실제 확정·전환 후보는 교체할 수 없습니다.");
        if(isNew&&("H".equals(source.extensionPossibility())||source.endDate().isAfter(YearMonth.from(today).plusMonths(2).atEndOfMonth())))throw state("신규 다음 자리는 3개월 이내 철수 예정이며 연장 가능성이 높지 않은 경우에만 가능합니다.");
    }
    private void requireOpen(Row row){if(!mapper.targetOpen(row))throw state("종료·중지한 대상은 선택할 수 없습니다.");}
    private String warningsJson(List<String> warnings){try{String value=json.writeValueAsString(new TreeSet<>(warnings));if(value.length()>500)throw RecommendationActionInput.invalid("confirmedWarnings");return value;}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("경고를 기록하지 못했습니다.");}}
    private YearMonth max(YearMonth a,YearMonth b){return a.isBefore(b)?b:a;}
    private LocalDate today(){return LocalDate.ofInstant(clock.instant(),TimeConfiguration.SEOUL);}
    private LocalDateTime now(){return LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);}
    private ApiException state(String message){return new ApiException(409,"INVALID_STATE",message);}
}
