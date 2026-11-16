package com.sicost.apiops;

import com.sicost.businessplansops.BusinessPlanPerformanceDtos.Actual;
import com.sicost.profitability.MonthlyCostDtos.*;
import com.sicost.structure.*;
import java.math.*;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.sicost.businessplansops.BusinessPlanActuals.*;
import static com.sicost.apiops.DashboardDtos.*;
import com.sicost.apiops.DashboardDtos.Part;

@Component
public class DashboardFinancialSources {
    private final ProfitStructureRepository profit;
    private final RevenueCompositionCalculation composition;
    public DashboardFinancialSources(ProfitStructureRepository profit,RevenueCompositionCalculation composition){this.profit=profit;this.composition=composition;}
    public ProfitStructureRepository.Read read(YearMonth month){return profit.read(new ProfitStructureDtos.Query(month.getYear(),month.toString(),"YTD","원"));}
    public record Utilization(String value,int idle) {}
    public Utilization utilization(ProfitStructureRepository.Read read,YearMonth selected) {
        var detail=read.details().get(selected.getYear());
        if(detail==null)return new Utilization(null,0);
        String key=selected.toString().replace("-","");var source=detail.sources();int denominator=0,assigned=0,idle=0;
        for(Person person:source.people()) {
            if(!person.employed(key))continue;
            var pay=source.payroll().stream().filter(p->p.month().equals(key)&&p.personId().equals(person.personId())).findFirst().orElse(null);
            String type=pay==null?person.employeeType():pay.employeeType(),direct=pay==null?person.directClass():pay.directClass();
            if(!Set.of("FT","CT","FL","OS").contains(type)||!"DRCT".equals(direct))continue;
            denominator++;
            var actual=detail.people().stream().filter(p->p.month().equals(key)&&p.personId().equals(person.personId())).findFirst().orElse(null);
            BigDecimal mm=actual==null?BigDecimal.ZERO:new BigDecimal(actual.assignmentMm());
            if(mm.signum()>0)assigned++;
            else if(source.states().stream().noneMatch(s->s.personId().equals(person.personId())&&"LEAVE".equals(s.status())&&!s.fromDate().isAfter(selected.atEndOfMonth())&&(s.toDate()==null||!s.toDate().isBefore(selected.atDay(1)))))idle++;
        }
        return new Utilization(denominator==0?null:BigDecimal.valueOf(assigned*100L).divide(BigDecimal.valueOf(denominator),1,RoundingMode.HALF_UP).toPlainString(),idle);
    }
    public List<Part> parts(ProfitStructureRepository.Read read,YearMonth month) {
        var detail=read.details().get(month.getYear());
        if(detail==null)return Collections.emptyList();
        String key=month.toString().replace("-","");var sources=detail.sources();
        record Row(String id,String name,String revenue,String cost) {}
        List<Row> rows=new ArrayList<>();
        composition.entries(read).stream().filter(e->e.month().equals(month.toString())).forEach(e->rows.add(new Row(e.partId(),e.partName(),e.revenue(),"0")));
        detail.people().stream().filter(p->p.month().equals(key)).flatMap(p->p.rows().stream())
            .filter(r->!com.sicost.profitability.MonthlyCostService.synthetic(r)&&!"IN".equals(r.contractType()))
            .forEach(r->{String name=sources.parts().stream().filter(p->p.partId().equals(r.projectSalesPartId())).map(com.sicost.profitability.MonthlyCostDtos.Part::name).findFirst().orElse("미지정");rows.add(new Row(r.projectSalesPartId(),name,"0",r.totalCost()));});
        for(var expense:detail.expenses())if(expense.month().equals(month.toString())) {
            String part=sources.projectParts().stream().filter(p->p.projectId().equals(expense.projectId())&&p.fromMonth().compareTo(key)<=0).max(Comparator.comparing(ProjectPart::fromMonth)).map(ProjectPart::partId).orElse(null);
            String name=sources.parts().stream().filter(p->p.partId().equals(part)).map(com.sicost.profitability.MonthlyCostDtos.Part::name).findFirst().orElse("미지정");
            rows.add(new Row(part,name,"0",money(expense.amount())));
        }
        Map<String,List<Row>> groups=new TreeMap<>();for(Row row:rows)groups.computeIfAbsent(Objects.toString(row.id(),""),id->new ArrayList<>()).add(row);
        List<Part> result=new ArrayList<>();groups.forEach((id,group)->{
            String sales=sum(group,Row::revenue),cost=sum(group,Row::cost);result.add(new Part(group.get(0).id(),group.get(0).name(),sales,cost,ratio(subtract(sales,cost),sales,true)));
        });
        result.sort(Comparator.comparing((Part p)->p.salesAmount()==null?BigDecimal.ZERO:new BigDecimal(p.salesAmount())).reversed().thenComparing(p->Objects.toString(p.partId(),"")));
        return List.copyOf(result);
    }
    public static String amount(Actual actual,String field){return actual==null?null:actual.amounts().get(field);}
}
