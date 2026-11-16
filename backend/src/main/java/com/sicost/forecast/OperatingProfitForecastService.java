package com.sicost.forecast;

import com.sicost.businessplansops.*;
import com.sicost.common.TimeConfiguration;
import com.sicost.profitability.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.profitability.MonthlyCostDtos.*;
import static com.sicost.businessplansops.BusinessPlanActuals.*;

/** Shared forward-only read projection; not an override of actual PL or monthly payroll readers. */
@Service
public class OperatingProfitForecastService {
    private final MonthlyCostRepository repository;private final MonthlyCostService costs;private final SalesForecastRepository assignments;
    private final SalesRevenueForecastService revenue;private final BusinessPlanPerformanceMapper finance;private final BusinessPlanActuals actuals;private final Clock clock;
    public OperatingProfitForecastService(MonthlyCostRepository repository,MonthlyCostService costs,SalesForecastRepository assignments,SalesRevenueForecastService revenue,BusinessPlanPerformanceMapper finance,BusinessPlanActuals actuals,Clock clock){this.repository=repository;this.costs=costs;this.assignments=assignments;this.revenue=revenue;this.finance=finance;this.actuals=actuals;this.clock=clock;}
    public record Month(String month,String projectRevenue,String salesCost,String sga,String previousAdjustment,String operatingProfit,List<String> reasons) {}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<Month> forecast() {
        var at=clock.instant().atZone(TimeConfiguration.SEOUL).toOffsetDateTime();YearMonth current=YearMonth.from(at),from=current.plusMonths(1),to=current.plusMonths(6);
        Sources original=repository.loadForecastRange(from,to);var forecast=assignments.load(current);var sales=revenue.calculate(forecast,at.toLocalDate());
        List<Assignment> future=new ArrayList<>();
        for(var a:forecast.assignments())for(var m:a.months()) {
            var s=a.source();String month=m.month().replace("-","");
            if(month.compareTo(original.period().fromMonth())<0||month.compareTo(original.period().toMonth())>0)continue;
            future.add(new Assignment(month,s.developerId(),s.projectId(),s.id(),decimal(m.monthlyMm()),decimal(m.appliedUnitPrice()),decimal(m.salesAmount()),m.fixed(),s.jobCode(),s.inputType(),s.contractDeveloperId(),s.status(),s.startDate(),s.endDate()));
        }
        // Historical/protected originals remain intact; the new range contains future months only.
        Sources projected=new Sources(original.period(),original.actor(),original.asOfDate(),original.people(),original.prices(),original.states(),original.payroll(),List.copyOf(future),List.copyOf(future),original.projects(),original.projectParts(),original.turnkey(),original.storedCosts(),original.standards(),original.parts(),original.closes(),original.allowanceCodes());
        var calculated=costs.calculate(projected);var financial=finance.financialSources(new BusinessPlanPerformanceDtos.YearRange(original.period().fromMonth(),original.period().toMonth()));List<Month> result=new ArrayList<>();
        for(var month:sales.revenueMonths()) {
            var common=actuals.calculated(month.month(),new BusinessPlanActuals.OperatingSources(calculated.items(),financial,projected),null,at);var v=common.amounts();
            String profit=add(subtract(subtract(month.projectRevenue(),v.get("salesCostAmt")),v.get("sgaTotAmt")),v.get("prevAdjAmt"));
            var reasons=common.completeness().issues().stream().filter(i->!Set.of("corpTaxAmt","netPrftAmt","salesAmt").contains(i.metric())).map(BusinessPlanPerformanceDtos.Issue::reason).distinct().toList();
            result.add(new Month(month.month(),month.projectRevenue(),v.get("salesCostAmt"),v.get("sgaTotAmt"),v.get("prevAdjAmt"),profit,reasons));
        }
        return List.copyOf(result);
    }
    private static BigDecimal decimal(String v){return v==null?null:new BigDecimal(v);}
}
