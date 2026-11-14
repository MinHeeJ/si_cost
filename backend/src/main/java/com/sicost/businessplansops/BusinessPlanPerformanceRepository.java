package com.sicost.businessplansops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.financial.FinancialCalculation;
import com.sicost.profitability.MonthlyCostService;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.businessplansops.BusinessPlanPerformanceDtos.*;
import static com.sicost.businessplansops.BusinessPlanActuals.api;

@Repository
public class BusinessPlanPerformanceRepository {
    private final BusinessPlanPerformanceMapper mapper;
    private final MonthlyCostService costs;
    private final BusinessPlanActuals actuals;
    private final CurrentUserProvider users;
    private final Clock clock;
    public BusinessPlanPerformanceRepository(BusinessPlanPerformanceMapper mapper,MonthlyCostService costs,
        BusinessPlanActuals actuals,CurrentUserProvider users,Clock clock) {
        this.mapper=mapper;this.costs=costs;this.actuals=actuals;this.users=users;this.clock=clock;
    }
    public record Read(Context context,List<Target> targets,List<Actual> months,List<String> notices) {}
    public Read read(Query query) {
        OffsetDateTime at=clock.instant().atZone(TimeConfiguration.SEOUL).toOffsetDateTime();
        YearMonth now=YearMonth.from(at),selected=YearMonth.parse(query.month());
        String base=mapper.baseMonth();
        if(base==null)throw new IllegalStateException("운영 시작월을 확인할 수 없습니다.");
        YearMonth start=YearMonth.parse(api(base));
        if(query.year()<start.getYear()||query.year()>now.getYear()||selected.isAfter(now))
            throw new ApiException(400,"INVALID_PERIOD","사업계획은 운영 시작연도부터 현재월까지 조회할 수 있습니다.","month",null);
        users.currentUser();
        var range=new YearRange(query.year()+"01",query.year()+"12");
        var targets=mapper.targets(range);var snapshots=mapper.snapshots(range);var closes=mapper.closes(range);
        var sources=mapper.financialSources(range);
        Map<String,Snapshot> stored=new HashMap<>();snapshots.forEach(s->stored.put(s.month(),s));
        Map<String,String> statuses=new HashMap<>();closes.forEach(c->statuses.put(c.month(),c.status()));
        List<FinancialCalculation.TaxMonth> taxMonths=new ArrayList<>();
        for(int m=1;m<=12;m++) {
            String month=YearMonth.of(query.year(),m).toString(),key=month.replace("-","");if(key.compareTo(base)<0)continue;
            var copy=stored.get(key);boolean valid=actuals.validClosed(copy);
            taxMonths.add(new FinancialCalculation.TaxMonth(month,statuses.getOrDefault(key,"WORK"),valid?copy.corpTaxAmt():null,valid?copy.fixed():null,valid?copy.migrated():null));
        }
        var allocation=FinancialCalculation.allocate(Integer.toString(query.year()),mapper.annualTax(Integer.toString(query.year())),taxMonths);
        Map<String,String> tax=new HashMap<>();allocation.months().forEach(m->tax.put(m.month(),m.amount()));
        // The personal validator is unchanged; historical months are selected directly from PL.
        var yearSources=costs.loadYear(query.year());var monthly=costs.calculate(yearSources);
        List<Actual> months=new ArrayList<>();List<String> notices=new ArrayList<>(allocation.warnings());
        for(int m=1;m<=12;m++) {
            String month=YearMonth.of(query.year(),m).toString(),key=month.replace("-","");
            String status=statuses.get(key);Snapshot s=stored.get(key);
            if(month.compareTo(query.month())>0)months.add(actuals.absent(month,"FUTURE",status,at));
            else if(key.compareTo(base)<0)months.add(s!=null&&"Y".equals(s.migrated())?actuals.stored(s,true,status,at):actuals.absent(month,"MISSING",status,at));
            else if("CLOSE".equals(status))months.add(actuals.validClosed(s)?actuals.stored(s,false,status,at):actuals.absent(month,"MISSING",status,at));
            else months.add(actuals.calculated(month,new BusinessPlanActuals.OperatingSources(monthly.items(),sources,yearSources),tax.get(month),at));
        }
        if(months.stream().anyMatch(Actual::provisional))notices.add("미마감 실적은 잠정입니다. 작업중 월의 원천 변경 시 달라질 수 있습니다.");
        if(months.stream().anyMatch(m->!m.completeness().estimated().isEmpty()))notices.add("예정 포함: 급여 등 예정 원천 사유를 확인해 주세요.");
        months.stream().filter(m->!m.completeness().missing().isEmpty()).forEach(m->m.completeness().issues().forEach(i->notices.add(m.month()+" "+i.reason())));
        Context context=new Context(query.year(),query.month(),api(base),now.toString(),"Asia/Seoul",at,"YTD","WON",true);
        return new Read(context,List.copyOf(targets),List.copyOf(months),notices.stream().distinct().toList());
    }
}
