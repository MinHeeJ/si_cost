package com.sicost.apiops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.validation.ProjectInputPolicy;
import com.sicost.profitability.MonthlyCostDtos.Project;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.apiops.ProjectAnalysisDtos.*;
import static com.sicost.apiops.ProjectCostCalculation.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class ProjectAnalysisService {
    public static final String NOTICE="일정·원가 신호는 1차 경보입니다. 실제 판단은 프로젝트 원가 분석의 상세 내역을 확인하세요.";
    private final ProjectAnalysisRepository repository;
    private final ProjectCostCalculation costs;
    private final ProjectStatusCalculation status;
    private final Clock clock;
    private final CurrentUserProvider users;
    public ProjectAnalysisService(ProjectAnalysisRepository repository,ProjectCostCalculation costs,ProjectStatusCalculation status,Clock clock,CurrentUserProvider users) {
        this.repository=repository;this.costs=costs;this.status=status;this.clock=clock;this.users=users;
    }
    public CostPage getProjectCosts(ProjectAnalysisInput.Query query) {
        users.currentUser();YearMonth month=query.value("month")==null?YearMonth.from(today()):ProjectAnalysisInput.month(query.value("month"));
        if(month.isAfter(YearMonth.from(today())))throw ProjectInputPolicy.invalid("month");
        Snapshot snapshot=repository.load(month);String id=query.value("projectId");
        if(id!=null) {
            Project project=snapshot.sources().projects().stream().filter(p->id.equals(p.projectId())).findFirst().orElseThrow(()->new ApiException(404,"NOT_FOUND","프로젝트를 찾을 수 없습니다."));
            if(!costEligible(project,month))throw new ApiException(409,"INVALID_PROJECT_STATUS","선택월 원가 분석 대상 프로젝트가 아닙니다.");
        }
        var rows=snapshot.sources().projects().stream().filter(p->costEligible(p,month)).filter(p->id==null||id.equals(p.projectId()))
            .filter(p->query.value("contractType")==null||query.value("contractType").equals(p.contractType()))
            .map(p->costs.row(snapshot,p,month)).filter(r->query.value("status")==null||query.value("status").equals(r.get("status")))
            .filter(r->!"true".equals(query.value("alertOnly"))||(int)r.get("alertCount")>0).sorted(order(query,false)).toList();
        return new CostPage(month.toString(),month.atEndOfMonth(),page(rows,query),rows.size(),query.page(),query.size(),costSummary(rows,month,snapshot));
    }
    public BoardPage getProjectStatusBoard(ProjectAnalysisInput.Query query) {
        users.currentUser();LocalDate day=today();YearMonth month=YearMonth.from(day);Snapshot snapshot=repository.load(month);
        String part=query.value("partId");if(part!=null&&!"UNASSIGNED".equals(part)&&snapshot.sources().parts().stream().noneMatch(p->part.equals(p.partId())))throw ProjectInputPolicy.invalid("partId");
        var rows=snapshot.sources().projects().stream().filter(p->Set.of("ING","PRE").contains(p.status())&&!p.toDate().isBefore(day))
            .filter(p->query.value("contractType")==null||query.value("contractType").equals(p.contractType()))
            .map(p->status.row(snapshot,p,costs.row(snapshot,p,month),day))
            .filter(r->query.value("light")==null||query.value("light").equals(r.get("light")))
            .filter(r->part==null||("UNASSIGNED".equals(part)?r.get("partId")==null:part.equals(r.get("partId"))))
            .sorted(order(query,true)).toList();
        var stale=rows.stream().filter(r->Boolean.TRUE.equals(r.get("updateWarning"))).map(r->fields("projectId",r.get("projectId"),"projectName",r.get("projectName"),"elapsedDays",r.get("elapsedDays"))).toList();
        Set<Object> ids=new HashSet<>();rows.forEach(r->ids.add(r.get("projectId")));
        long high=snapshot.issues().stream().filter(i->ids.contains(i.projectId())&&!"CLOSE".equals(i.status())&&"HIGH".equals(i.importance())).count();
        var summary=fields("baseDate",day,"projectCount",rows.size(),"normalCount",count(rows,"light","정상"),"warningCount",count(rows,"light","주의"),
            "dangerCount",count(rows,"light","위험"),"openIssueCount",integerSum(rows,"openIssueCount"),"openHighIssueCount",high,"overdueIssueCount",integerSum(rows,"overdueIssueCount"),
            "staleProjectCount",stale.size(),"unknownCostProjectCount",count(rows,"costUnknown",true));
        return new BoardPage(day,month.toString(),page(rows,query),rows.size(),query.page(),query.size(),summary,stale,NOTICE);
    }
    private Map<String,Object> costSummary(List<Map<String,Object>> rows,YearMonth month,Snapshot snapshot) {
        var finance=rows.stream().filter(r->!"IN".equals(r.get("contractType"))).toList();var research=rows.stream().filter(r->"IN".equals(r.get("contractType"))).toList();
        var completeness=rows.stream().map(r->(Completeness)r.get("completeness")).toList();
        var all=new Completeness(completeness.stream().allMatch(Completeness::costComplete),completeness.stream().allMatch(Completeness::revenueComplete),
            completeness.stream().anyMatch(Completeness::estimated),completeness.stream().flatMap(c->c.issues().stream()).toList(),
            (int)completeness.stream().filter(c->c.unknownProjectCount()>0).count(),completeness.stream().map(Completeness::rangeStartMonth).min(String::compareTo).orElse(apiMonth(snapshot.sources().period().baseMonth())),month.toString(),
            completeness.stream().map(Completeness::missingBeforeMonth).filter(Objects::nonNull).min(String::compareTo).orElse(null));
        return fields("month",month.toString(),"total",rows.size(),"cumulativeRevenue",total(finance,"cumulativeRevenue"),"cumulativeProfit",total(finance,"cumulativeProfit"),
            "lowMarginCount",alarmCount(rows,"LOW_MARGIN"),"budgetOverCount",alarmCount(rows,"BUDGET_OVER"),"researchCost",total(research,"cumulativeCost"),
            "unknownCostCount",rows.stream().filter(r->r.get("cumulativeCost")==null).count(),"unknownRevenueCount",finance.stream().filter(r->r.get("cumulativeRevenue")==null).count(),
            "unknownMarginCount",finance.stream().filter(r->r.get("cumulativeMargin")==null).count(),"completeness",all);
    }
    @SuppressWarnings("unchecked") private static long alarmCount(List<Map<String,Object>> rows,String kind) {return rows.stream().filter(r->((List<Map<String,Object>>)r.get("alerts")).stream().anyMatch(a->kind.equals(a.get("kind")))).count();}
    public static boolean costEligible(Project p,YearMonth month) {
        return !Set.of("PRE","LOSE").contains(p.status())&&!p.fromDate().isAfter(month.atEndOfMonth())&&!p.toDate().isBefore(Year.of(month.getYear()).atDay(1));
    }
    private LocalDate today(){return clock.instant().atZone(TimeConfiguration.SEOUL).toLocalDate();}
    private static long count(List<Map<String,Object>> rows,String key,Object value){return rows.stream().filter(r->value.equals(r.get(key))).count();}
    private static int integerSum(List<Map<String,Object>> rows,String key){return rows.stream().mapToInt(r->(int)r.get(key)).sum();}
    private static String total(List<Map<String,Object>> rows,String key){BigDecimal result=BigDecimal.ZERO;for(var row:rows){if(row.get(key)==null)return null;result=result.add(decimal(row.get(key)));}return amount(result);}
    private static List<Map<String,Object>> page(List<Map<String,Object>> rows,ProjectAnalysisInput.Query query) {
        long offset=((long)query.page()-1)*query.size();if(offset>=rows.size())return Collections.emptyList();return rows.subList((int)offset,(int)Math.min(rows.size(),offset+query.size()));
    }
    private static int severity(Object light){return "위험".equals(light)?0:"주의".equals(light)?1:2;}
    private static Comparator<Map<String,Object>> order(ProjectAnalysisInput.Query query,boolean board) {
        Comparator<Map<String,Object>> comparator;
        if(query.sort()==null)comparator=board?Comparator.comparingInt(r->severity(r.get("light"))):Comparator.<Map<String,Object>>comparingInt(r->(int)r.get("alertCount")).reversed();
        else comparator=(a,b)->compare(a.get(query.sort()),b.get(query.sort()),query.sort(),query.direction());
        if(query.sort()==null)comparator=comparator.thenComparing(r->r.get("projectName").toString());
        return comparator.thenComparing(r->r.get("projectId").toString());
    }
    private static int compare(Object a,Object b,String key,String direction) {
        if(a==null)return b==null?0:1;if(b==null)return -1;
        int result="light".equals(key)?Integer.compare(severity(a),severity(b)):Set.of("projectName","lastUpdatedDate").contains(key)?a.toString().compareTo(b.toString()):decimal(a).compareTo(decimal(b));
        return "DESC".equals(direction)?-result:result;
    }
}
