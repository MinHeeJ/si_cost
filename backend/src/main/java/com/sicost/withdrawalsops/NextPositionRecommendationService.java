package com.sicost.withdrawalsops;

import com.sicost.apiops.WithdrawalPositionCalculator;
import com.sicost.apiops.WorkforceForecastService;
import com.sicost.candidates.RecommendationActionDtos;
import com.sicost.candidates.RecommendationActionRepository;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.validation.ProjectInputPolicy;
import com.sicost.workforceforecastsops.WorkforceForecastInput;
import com.sicost.workforcerecommendations.WorkforceRecommendationRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.workforcerecommendations.WorkforceRecommendationDtos.*;

@Service
public class NextPositionRecommendationService {
    private final NextPositionRepository repository;
    private final WorkforceRecommendationRepository recommendations;
    private final RecommendationActionRepository actions;
    private final WorkforceForecastService forecasts;
    private final CurrentUserProvider users;
    private final Clock clock;
    public NextPositionRecommendationService(NextPositionRepository repository,WorkforceRecommendationRepository recommendations,RecommendationActionRepository actions,WorkforceForecastService forecasts,CurrentUserProvider users,Clock clock){this.repository=repository;this.recommendations=recommendations;this.actions=actions;this.forecasts=forecasts;this.users=users;this.clock=clock;}
    public record Shortage(String month,String jobCode,String shortagePeople) {}
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public Data create(String assignmentId,WithdrawalActionInput.Recommendation input){
        String actor=users.currentUser().userId();LocalDate day=LocalDate.ofInstant(clock.instant(),TimeConfiguration.SEOUL);
        ProjectInputPolicy.year(input.year(),repository.cashBase(),clock);
        var source=repository.lock(assignmentId);repository.requireActive(source);
        if(input.year()<day.getYear()||source.withdrawalDate().isBefore(day)||source.withdrawalDate().getYear()!=input.year()||"LOSE".equals(source.projectStatus()))throw state("과거연도·철수 완료·실주 배정은 다음 자리를 추천할 수 없습니다.");
        repository.requireRevision(assignmentId,input.expectedRevision());
        var leaves=repository.leaves(source.developerId());
        if(!WithdrawalPositionCalculator.eligible(source,day,leaves))throw state("활성 직접 인력의 입퇴사·현재 휴직 상태를 확인해 주세요.");
        var next=repository.next(source);
        if(next.stream().anyMatch(n->!"TENTATIVE".equals(n.kind())))throw state("실제 확정 또는 전환 배정 후보는 바꿀 수 없습니다.");
        if(next.isEmpty()&&("H".equals(source.extensionPossibility())||source.withdrawalDate().isAfter(YearMonth.from(day).plusMonths(2).atEndOfMonth())))throw state("신규 다음 자리는 3개월 안 철수하며 연장가능성이 높지 않은 인력만 추천합니다.");
        var positions=repository.positions(source.developerId());var top=WithdrawalPositionCalculator.calculate(source,day,positions,leaves);

        int excluded=positions.size()-top.size();
        Exclusions exclusions=new Exclusions(positions.size(),Math.max(0,excluded),excluded>0?List.of(new ReasonCount("자리 조건 또는 상위 5위 외",excluded)):Collections.emptyList(),Collections.emptyList(),true);
        List<Object> shortages=forecasts.forecast(new WorkforceForecastInput.Query(YearMonth.from(day).plusMonths(1),6,true,"WON")).jobDiagnostics().stream()
            .filter(j->source.developerJob().equals(j.jobCode())&&"SHORT".equals(j.month().status())&&j.month().difference()!=null)
            .map(j->(Object)new Shortage(j.month().month(),j.jobCode(),new BigDecimal(j.month().difference()).negate().toPlainString())).toList();
        if(top.isEmpty())return new Data(null,"NEXT",null,assignmentId,null,null,Collections.emptyList(),exclusions,"맞는 자리가 없습니다.",input.expectedRevision(),shortages);
        List<NextPositionScoreDetail> details=top.stream().map(r->NextPositionScoreDetail.from(source,r)).toList();
        List<String> encoded=details.stream().map(d->recommendations.encode(d,d.reason())).toList();
        String id=repository.nextId();LocalDateTime time=LocalDateTime.ofInstant(clock.instant(),TimeConfiguration.SEOUL);
        List<Candidate> candidates=new ArrayList<>();
        for(int i=0;i<top.size();i++){
            var r=top.get(i);var p=r.position();var d=details.get(i);
            String grade=p.gradeCode()==null||p.gradeCode().isBlank()?"MD":p.gradeCode();
            Row row=new Row(id,i+1,"NEXT",p.opportunityId(),Integer.toString(p.needSn()),null,p.jobCode(),AssignmentMonthlyService.db(r.actualStart().toString()),grade,source.developerId(),Integer.toString(r.total()),encoded.get(i),d.reason(),null,null,null,null,actor,time);
            repository.insert(row,assignmentId);
            var actionRow=new RecommendationActionDtos.Row(id,i+1,"NEXT",p.opportunityId(),row.needSn(),null,p.jobCode(),row.startMonth(),source.developerId(),row.scoreDetail(),null,null,null,null,time);
            actions.storeOriginal(actionRow);var revisions=actions.current(actionRow);
            Developer developer=new Developer(source.developerId(),source.name(),source.managementNumber(),source.developerJob(),source.subJobCode(),source.gradeCode(),source.employeeTypeCode(),source.developerId());
            List<OtherTentative> others=next.stream().filter(n->!Objects.equals(n.opportunityId(),p.opportunityId())).map(n->new OtherTentative(n.tentativeAssignmentId(),n.opportunityId(),n.name(),recommendations.mapper().tentatives().stream().filter(t->Objects.equals(t.tentativeAssignmentId(),n.tentativeAssignmentId())).findFirst().orElseThrow().needSn(),n.startMonth())).toList();
            candidates.add(new Candidate(i+1,developer,p.opportunityId(),row.needSn(),p.jobCode(),r.actualStart().toString(),grade,row.totalScore(),d.items(),recommendations.decode(row.scoreDetail()),row.reason(),others,new Selection(null,null,null,null,null,null,null,null),revisions.expectedRevision(),revisions.targetRevision(),revisions.candidateRevision(),recommendations.nextPosition(row)));
        }
        recommendations.invalidate();return new Data(id,"NEXT",null,assignmentId,WorkforceRecommendationRepository.time(time),actor,List.copyOf(candidates),exclusions,"추천은 참고용입니다. 가배정은 담당자가 정합니다.",recommendations.revision(candidates),shortages);
    }
    private ApiException state(String message){return new ApiException(409,"INVALID_STATE",message);}
}
