package com.sicost.progress;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.ProjectProgressAggregateRevision;
import com.sicost.common.concurrent.ProjectProgressView;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.validation.ProjectInputPolicy;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.progress.ProjectProgressDtos.*;
import static com.sicost.progress.ProjectProgressCalculation.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ProjectProgressQueryService implements ProjectProgressView {
    private final ProjectProgressRepository repository;
    private final ProjectProgressAggregateRevision revisions;
    private final ProjectProgressCostSource costs;
    private final CurrentUserProvider users;
    private final Clock clock;
    public ProjectProgressQueryService(ProjectProgressRepository repository,ProjectProgressAggregateRevision revisions,
        ProjectProgressCostSource costs,CurrentUserProvider users,Clock clock) {
        this.repository=repository;this.revisions=revisions;this.costs=costs;this.users=users;this.clock=clock;
    }
    @Override
    public Data getProjectProgress(String id,Integer requestedYear) {
        users.currentUser();LocalDate today=ProjectInputPolicy.today(clock);int year=requestedYear==null?today.getYear():requestedYear;
        if(year<1 || year>9999)throw ProjectInputPolicy.invalid("year");
        var p=repository.required(id,today);
        if(p.startDate().getYear()>year || p.endDate().getYear()<year)throw ProjectInputPolicy.invalid("year");
        var raw=repository.steps(id);var steps=raw.stream().map(r->step(r,today)).toList();
        var memos=repository.memos(id).stream().sorted(Comparator.comparing(MemoRow::writtenDate).thenComparingInt(MemoRow::memoSn).reversed()).map(ProjectProgressCalculation::memo).toList();
        var issues=repository.issues(id).stream().map(r->issue(r,today)).toList();
        var cost=costs.load(p,today);var reasons=new ArrayList<Reason>();
        BigDecimal plan=new BigDecimal(plan(p,raw,today));
        BigDecimal actual=Set.of("MO","DP").contains(p.contractType())?plan:memos.stream()
            .filter(m->!m.writtenDate().isAfter(today)&&m.actualProgress()!=null).findFirst()
            .map(m->BigDecimal.valueOf(m.actualProgress()).setScale(1)).orElse(new BigDecimal("0.0"));
        BigDecimal gap=plan.subtract(actual);
        BigDecimal usage="TK".equals(p.contractType()) && p.budget()!=null && p.budget().signum()>0 && cost.amount()!=null
            ?cost.amount().multiply(new BigDecimal("100")).divide(p.budget(),1,RoundingMode.HALF_UP):null;
        BigDecimal costGap=usage==null?null:usage.subtract(actual);
        threshold(reasons,"일정 늦음",gap,"10.0","5.0","계획 − 실적 (P − A)");
        if(costGap!=null)threshold(reasons,"진척대비원가",costGap,"15.0","5.0","원가 사용률 − 실적 (U − A), U = C/B × 100");
        int open=(int)issues.stream().filter(i->!i.status().equals("CLOSE")).count();
        int overdue=(int)issues.stream().filter(Issue::overdue).count();
        long high=issues.stream().filter(i->i.overdue()&&i.importance().equals("HIGH")).count();
        if(high>0)reasons.add(new Reason("기한지난HIGH","위험",Long.toString(high),"1","status ≠ CLOSE, 기한 < 기준일, 중요도 HIGH"));
        if(overdue>0)reasons.add(new Reason("기한지난이슈","주의",Integer.toString(overdue),"1","status ≠ CLOSE, 기한 < 기준일"));
        LocalDate updated=memos.stream().map(Memo::writtenDate).max(LocalDate::compareTo).orElse(null);
        LocalDate fallback=p.registeredAt().toLocalDate().isAfter(p.startDate())?p.registeredAt().toLocalDate():p.startDate();
        int elapsed=(int)Math.max(0,ChronoUnit.DAYS.between(updated==null?fallback:updated,today));
        if(elapsed>14)reasons.add(new Reason("메모경과","주의",Integer.toString(elapsed),">14","기준일 − MAX(WRT_DT), 메모 없음: max(등록일, 계약시작일), 미래 0"));
        String light=reasons.stream().anyMatch(r->r.light().equals("위험"))?"위험":reasons.isEmpty()?"정상":"주의";
        Step current=steps.stream().filter(s->s.status().equals("지연")).findFirst().orElseGet(()->steps.stream().filter(s->s.status().equals("진행중")).findFirst().orElseGet(()->steps.stream().filter(s->s.status().equals("예정")).findFirst().orElse(null)));
        boolean valid=valid(p,raw);String basis=valid?"단계 기준":raw.isEmpty()?"기간 기준·단계 미입력":"기간 기준·단계 집합 확인 필요";
        var readOnly=new ArrayList<String>();
        if(year!=today.getYear())readOnly.add("진행 자료는 현재연도에서만 편집할 수 있습니다.");
        if(p.endDate().isBefore(today)||!Set.of("ING","PRE").contains(p.sourceStatus()))readOnly.add("종료·실주 프로젝트의 진행 자료는 조회만 할 수 있습니다.");
        var status=new Status(id,p.projectName(),p.contractType(),p.sourceStatus(),p.partId(),p.partName(),p.hq(),repository.pm(id),light,List.copyOf(reasons),
            "TK".equals(p.contractType())&&usage==null,plan.toPlainString(),actual.toPlainString(),decimal(usage),gap.toPlainString(),decimal(costGap),current,
            !steps.isEmpty()&&steps.stream().allMatch(s->s.completedDate()!=null),!steps.isEmpty(),basis,open,overdue,updated,elapsed,elapsed>14,today,YearMonth.from(today).toString(),cost.completeness());
        String message=valid?null:raw.isEmpty()?"기본 단계가 없습니다. 기본 단계 초기화로 준비해 주세요.":"기존 단계 집합을 확인해 주세요. 원본 단계는 보존하며 기간 기준으로 계산합니다.";
        return new Data(id,year,revisions.revision(id),!readOnly.isEmpty(),List.copyOf(readOnly),status,steps,memos,issues,message,NOTICE);
    }
    public HistoryPage getProjectProgressHistory(String id,HistoryQuery q) {
        users.currentUser();repository.required(id,ProjectInputPolicy.today(clock));
        if(q.targetKey()!=null && !belongs(id,q))throw ProjectInputPolicy.invalid("targetKey");
        var all=repository.history(id).stream().filter(r->q.targetTable()==null||q.targetTable().equals(r.targetTable()))
            .filter(r->q.targetKey()==null||q.targetKey().equals(r.targetKey())).toList();
        long offset=(long)(q.page()-1)*q.size();
        var entries=all.stream().skip(offset).limit(q.size()).map(r->new HistoryEntry(r.historyId(),r.targetTable(),r.targetKey(),r.changeType(),r.itemName(),r.before(),r.after(),r.reason(),actor(r.registeredBy(),r.registeredName()),time(r.registeredAt()))).toList();
        return new HistoryPage(id,entries,all.size(),q.page(),q.size());
    }
    private boolean belongs(String id,HistoryQuery q) {
        return (q.targetTable()==null || q.targetTable().equals("TB_PJ_STEP"))&&repository.steps(id).stream().anyMatch(r->q.targetKey().equals(id+":"+r.stepSn()))
            || (q.targetTable()==null || q.targetTable().equals("TB_PJ_WEEK_MEMO"))&&repository.memos(id).stream().anyMatch(r->q.targetKey().equals(id+":"+r.memoSn()))
            || (q.targetTable()==null || q.targetTable().equals("TB_PJ_ISSUE"))&&repository.issues(id).stream().anyMatch(r->q.targetKey().equals(Long.toString(r.issueId())));
    }
    public Preview preview(String id,Dates input) {
        users.currentUser();LocalDate today=ProjectInputPolicy.today(clock);ProjectRow p=repository.required(id,today);
        if(p.startDate().getYear()>input.year() || p.endDate().getYear()<input.year())throw ProjectInputPolicy.invalid("year");
        var result=dates(p,repository.steps(id),input.steps());
        return new Preview(id,revisions.revision(id),result.stream().map(r->step(r,today)).toList());
    }
    private static String decimal(BigDecimal value){return value==null?null:value.toPlainString();}
    private static void threshold(List<Reason> reasons,String kind,BigDecimal value,String danger,String warning,String formula) {
        if(value.compareTo(new BigDecimal(danger))>=0)reasons.add(new Reason(kind,"위험",value.toPlainString(),danger,formula));
        else if(value.compareTo(new BigDecimal(warning))>=0)reasons.add(new Reason(kind,"주의",value.toPlainString(),warning,formula));
    }
}
