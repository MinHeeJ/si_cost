package com.sicost.structure;

import com.sicost.businessplansops.*;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.financial.FinancialCalculation;
import com.sicost.profitability.MonthlyCostDtos.*;
import com.sicost.profitability.MonthlyCostService;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Repository;
import static com.sicost.businessplansops.BusinessPlanPerformanceDtos.*;
import static com.sicost.businessplansops.BusinessPlanActuals.api;
import static com.sicost.structure.ProfitStructureDtos.*;
import com.sicost.structure.ProfitStructureDtos.Query;

@Repository
public class ProfitStructureRepository {
    private final BusinessPlanPerformanceMapper financial;
    private final ProfitStructureMapper mapper;
    private final MonthlyCostService costs;
    private final BusinessPlanActuals actuals;
    private final CurrentUserProvider users;
    private final Clock clock;
    public ProfitStructureRepository(BusinessPlanPerformanceMapper financial,ProfitStructureMapper mapper,
        MonthlyCostService costs,BusinessPlanActuals actuals,CurrentUserProvider users,Clock clock) {
        this.financial=financial;this.mapper=mapper;this.costs=costs;this.actuals=actuals;this.users=users;this.clock=clock;
    }
    public record Details(Sources sources,List<MonthlyPersonCost> people,List<ProjectAmount> bills,List<ProjectAmount> expenses) {}
    public record Read(Context context,List<Actual> months,Map<String,Actual> all,Map<Integer,Details> details,
        List<ProjectCustomer> customers,List<String> notices) {}
    public Read read(Query query) {
        OffsetDateTime at=clock.instant().atZone(TimeConfiguration.SEOUL).toOffsetDateTime();
        String base=financial.baseMonth();if(base==null)throw new IllegalStateException("운영 시작월이 없습니다.");
        int minimum=Integer.parseInt(base.substring(0,4))-1;
        YearMonth now=YearMonth.from(at),selected=YearMonth.parse(query.month());
        if(query.year()<minimum||query.year()>now.getYear()||selected.isAfter(now))throw new ApiException(400,"INVALID_PERIOD","운영 시작 전년부터 현재월까지 조회할 수 있습니다.","month",null);
        users.currentUser();
        Map<String,Actual> all=new LinkedHashMap<>();Map<Integer,Details> details=new HashMap<>();List<String> notices=new ArrayList<>();
        for(int year=Math.max(minimum,query.year()-1);year<=query.year();year++) {
            YearRange range=new YearRange(year+"01",year+"12");
            Map<String,Snapshot> stored=new HashMap<>();financial.snapshots(range).forEach(s->stored.put(s.month(),s));
            Map<String,String> statuses=new HashMap<>();financial.closes(range).forEach(s->statuses.put(s.month(),s.status()));
            boolean operating=year>=Integer.parseInt(base.substring(0,4));
            Map<String,String> taxes=new HashMap<>();Details detail=null;List<Source> sources=List.of();
            if(operating) {
                List<FinancialCalculation.TaxMonth> months=new ArrayList<>();
                for(int m=1;m<=12;m++) {
                    String month=YearMonth.of(year,m).toString(),key=month.replace("-","");if(key.compareTo(base)<0)continue;
                    Snapshot copy=stored.get(key);boolean valid=actuals.validClosed(copy);
                    months.add(new FinancialCalculation.TaxMonth(month,statuses.getOrDefault(key,"WORK"),valid?copy.corpTaxAmt():null,valid?copy.fixed():null,valid?copy.migrated():null));
                }
                var allocation=FinancialCalculation.allocate(Integer.toString(year),financial.annualTax(Integer.toString(year)),months);
                allocation.months().forEach(m->taxes.put(m.month(),m.amount()));notices.addAll(allocation.warnings());
                Sources originals=costs.loadYear(year);var calculated=costs.calculate(originals);
                detail=new Details(originals,calculated.items(),mapper.bills(range),mapper.expenses(range));details.put(year,detail);
                sources=financial.financialSources(range);
            }
            for(int m=1;m<=12;m++) {
                String month=YearMonth.of(year,m).toString(),key=month.replace("-","");String status=statuses.get(key);Snapshot s=stored.get(key);Actual result;
                if(month.compareTo(query.month())>0)result=actuals.absent(month,"FUTURE",status,at);
                else if(key.compareTo(base)<0)result=s!=null&&"Y".equals(s.migrated())?actuals.stored(s,true,status,at):actuals.absent(month,"MISSING",status,at);
                else if("CLOSE".equals(status))result=actuals.validClosed(s)?actuals.stored(s,false,status,at):actuals.absent(month,"MISSING",status,at);
                else result=actuals.calculated(month,new BusinessPlanActuals.OperatingSources(detail.people(),sources,detail.sources()),taxes.get(month),at);
                all.put(month,result);
            }
        }
        List<Actual> months=all.values().stream().filter(m->m.month().startsWith(query.year()+"-")).toList();
        if(months.stream().anyMatch(Actual::provisional))notices.add("잠정: 미마감 월은 작업중이며 원천 변경 시 다시 계산합니다.");
        if(months.stream().anyMatch(m->!m.completeness().estimated().isEmpty()))notices.add("예정 포함: 급여 등 예정 원천의 사유를 확인해 주세요.");
        months.stream().flatMap(m->m.completeness().issues().stream()).forEach(i->notices.add(i.month()+" "+i.reason()));
        return new Read(new Context(query.year(),query.month(),api(base),now.toString(),"Asia/Seoul",at,query.aggregation(),query.amountUnit(),true),
            months,Collections.unmodifiableMap(all),details,mapper.customers(),notices.stream().distinct().toList());
    }
}
