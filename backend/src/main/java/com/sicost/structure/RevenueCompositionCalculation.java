package com.sicost.structure;

import com.sicost.businessplansops.BusinessPlanPerformanceDtos.*;
import com.sicost.profitability.MonthlyCostDtos.*;
import com.sicost.profitability.MonthlyCostService;
import com.sicost.profitability.TurnkeyRevenueCalculation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.sicost.businessplansops.BusinessPlanActuals.*;
import static com.sicost.structure.ProfitStructureDtos.*;
import static com.sicost.structure.ProfitStructureRepository.*;

@Component
public class RevenueCompositionCalculation {
    public static final String MIGRATED="월별 손익만 이관되어 세부 구성이 없습니다";
    private final TurnkeyRevenueCalculation turnkey;
    public RevenueCompositionCalculation(TurnkeyRevenueCalculation turnkey) {this.turnkey=turnkey;}
    public record Entry(String month,String projectId,String type,String customerId,String customerName,String partId,String partName,String revenue,String labor) {}
    public List<Entry> entries(Read read) {
        List<Entry> entries=new ArrayList<>();Map<String,ProjectCustomer> customers=new HashMap<>();read.customers().forEach(c->customers.put(c.projectId(),c));
        for(var detail:read.details().values()) {
            Sources sources=detail.sources();
            for(Actual actual:read.months()) {
                if(!Set.of("CALCULATED","CLOSED_SNAPSHOT").contains(actual.sourceKind()))continue;
                String month=actual.month(),key=month.replace("-","");if(YearMonth.parse(month).getYear()!=sources.period().year())continue;
                var rows=detail.people().stream().filter(p->key.equals(p.month())).flatMap(p->p.rows().stream()).filter(r->!MonthlyCostService.synthetic(r)).toList();
                for(var row:rows) {
                    if(!Set.of("TK","MO","DP").contains(Objects.toString(row.contractType(),"")))continue;
                    String labor=Set.of("ALLOC","PRE").contains(row.costClass())?row.laborAllocated():"0";
                    String revenue=Set.of("MO","DP").contains(row.contractType())&&"ALLOC".equals(row.costClass())&&!"FREE".equals(row.inputType())?row.personalRevenue():"0";
                    entries.add(entry(month,row.projectId(),row.contractType(),row.projectSalesPartId(),revenue,labor,sources,customers));
                }
                for(var bill:detail.bills())if(month.equals(bill.month())) {
                    String part=sources.projectParts().stream().filter(p->p.projectId().equals(bill.projectId())&&p.fromMonth().compareTo(key)<=0).max(Comparator.comparing(ProjectPart::fromMonth)).map(ProjectPart::partId).orElse(null);
                    entries.add(entry(month,bill.projectId(),"TK",part,money(bill.amount()),"0",sources,customers));
                }
            }
        }
        return List.copyOf(entries);
    }
    private Entry entry(String month,String project,String type,String part,String revenue,String labor,Sources sources,Map<String,ProjectCustomer> customers) {
        var customer=customers.get(project);var found=sources.parts().stream().filter(p->p.partId().equals(part)).findFirst().orElse(null);
        String name=part==null?"미지정":found==null?part:"Y".equals(found.headquarters())?"본사":found.name();
        return new Entry(month,project,type,customer==null?null:customer.customerId(),customer==null||customer.customerName()==null?"미지정":customer.customerName(),part,name,revenue,labor);
    }
    public List<String> topCustomers(List<Entry> entries,List<String> months) {
        return groups(entries,months,"CUSTOMER",null,null).stream().limit(5).map(g->Objects.toString(g.groupId(),"UNASSIGNED")).toList();
    }
    public Composition composition(List<Entry> entries,List<Actual> months,Map<String,String> amounts,List<String> top) {
        if(months.stream().anyMatch(m->m.migrated()||m.completeness().issues().stream().anyMatch(i->"MIGRATED_MONTH_MISSING".equals(i.code()))))return new Composition(false,MIGRATED,false,null,List.of(),List.of(),List.of());
        List<String> keys=months.stream().map(Actual::month).toList();String total=amounts.get("salesAmt");
        var types=groups(entries,keys,"CONTRACT_TYPE",total,null);var customers=groups(entries,keys,"CUSTOMER",total,top);var parts=groups(entries,keys,"SALES_PART",total,null);
        String groupTotal=types.isEmpty()?"0":sum(types,Group::salesAmt),difference=subtract(total,groupTotal);
        boolean complete=difference!=null&&new BigDecimal(difference).signum()==0&&months.stream().allMatch(m->m.completeness().revenueComplete())&&types.stream().allMatch(g->g.allocatedLaborCostAmt()!=null);
        return new Composition(true,complete?null:"구성 원천 미확인 또는 회사 매출과 차이가 있습니다.",complete,difference,types,customers,parts);
    }
    private List<Group> groups(List<Entry> entries,List<String> months,String kind,String total,List<String> top) {
        Map<String,List<Entry>> grouped=new TreeMap<>();
        for(Entry entry:entries)if(months.contains(entry.month())) {
            String id=switch(kind){case "CONTRACT_TYPE"->entry.type();case "CUSTOMER"->Objects.toString(entry.customerId(),"UNASSIGNED");default->Objects.toString(entry.partId(),"UNASSIGNED");};
            if(top!=null&&!top.contains(id))id="OTHER";
            grouped.computeIfAbsent(id,k->new ArrayList<>()).add(entry);
        }
        List<Group> result=new ArrayList<>();
        grouped.forEach((id,list)->{
            String revenue=sum(list,Entry::revenue),labor=sum(list,Entry::labor);if(!"CONTRACT_TYPE".equals(kind)&&"0".equals(revenue)&&"0".equals(labor))return;
            String contribution=subtract(revenue,labor);Entry first=list.get(0);
            String name="OTHER".equals(id)?"그 밖":switch(kind){case "CONTRACT_TYPE"->id;case "CUSTOMER"->first.customerName();default->first.partName();};
            result.add(new Group(kind,"UNASSIGNED".equals(id)?null:id,name,"OTHER".equals(id),revenue,labor,contribution,ratio(revenue,total,false),ratio(contribution,revenue,false),null,contribution!=null&&new BigDecimal(contribution).signum()<0,revenue!=null));
        });
        result.sort(Comparator.comparing((Group g)->g.salesAmt()==null?BigDecimal.ZERO:new BigDecimal(g.salesAmt())).reversed().thenComparing(g->Objects.toString(g.groupId(),"")));
        return List.copyOf(result);
    }
    public Reference reference(Read read,List<Actual> months) {
        if(months.stream().anyMatch(m->m.migrated()||m.completeness().issues().stream().anyMatch(i->"MIGRATED_MONTH_MISSING".equals(i.code()))))return new Reference(false,null,false,MIGRATED,List.of());
        List<Issue> issues=new ArrayList<>();List<String> values=new ArrayList<>();
        for(Actual actual:months) {
            Details detail=read.details().get(YearMonth.parse(actual.month()).getYear());
            if(detail==null||!Set.of("CALCULATED","CLOSED_SNAPSHOT").contains(actual.sourceKind())) {values.add(null);continue;}
            String key=actual.month().replace("-","");var sources=detail.sources();
            var rows=detail.people().stream().filter(p->key.equals(p.month())).flatMap(p->p.rows().stream()).toList();
            values.add(sum(rows.stream().filter(r->Set.of("MO","DP").contains(Objects.toString(r.contractType(),""))&&"ALLOC".equals(r.costClass())&&!"FREE".equals(r.inputType())&&!MonthlyCostService.synthetic(r)).toList(),MonthlyCostRow::personalRevenue));
            for(Project project:sources.projects())if("TK".equals(project.contractType())&&!Set.of("PRE","LOSE").contains(project.status())) {
                var selected=turnkey.selected(sources,project.projectId());String amount;
                if(!selected.isEmpty())amount=money(selected.stream().filter(c->key.equals(c.month())&&Set.of("DEV","ITEM").contains(c.rowType())).map(c->c.amount()).reduce(BigDecimal.ZERO,BigDecimal::add));
                else {
                    var projectRows=rows.stream().filter(r->project.projectId().equals(r.projectId())).toList();
                    String cost=add(sum(projectRows,MonthlyCostRow::laborAllocated),sum(projectRows,MonthlyCostRow::directExpense),sum(detail.expenses().stream().filter(e->actual.month().equals(e.month())&&project.projectId().equals(e.projectId())).toList(),e->money(e.amount())));
                    amount=cost==null||project.budget()==null||project.budget().signum()<=0||project.contractAmount()==null?null:new BigDecimal(cost).multiply(project.contractAmount()).divide(project.budget(),0,RoundingMode.DOWN).toPlainString();
                }
                values.add(amount);if(amount==null)issues.add(new Issue(actual.month(),"referenceRevenue","REFERENCE_UNCONFIRMED",project.projectId()+" 배분표 또는 저장 예산·전체 월 원가 미확인",false));
            }
        }
        String amount=sum(values,v->v);return new Reference(true,amount,amount!=null,amount==null?"참고 매출 원천 미확인":null,List.copyOf(issues));
    }
}
