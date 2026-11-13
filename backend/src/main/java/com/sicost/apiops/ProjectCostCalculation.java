package com.sicost.apiops;

import com.sicost.profitability.MonthlyCostDtos.*;
import com.sicost.profitability.TurnkeyRevenueCalculation;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import static com.sicost.apiops.ProjectAnalysisDtos.*;

@Component("projectListCostCalculation")
public class ProjectCostCalculation {
    private final TurnkeyRevenueCalculation turnkey;
    public ProjectCostCalculation(TurnkeyRevenueCalculation turnkey) {this.turnkey=turnkey;}
    public record Fraction(BigDecimal numerator,BigDecimal denominator) {
        public Fraction add(Fraction other) {return new Fraction(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),denominator.multiply(other.denominator));}
        public Fraction subtract(BigDecimal value) {return new Fraction(numerator.subtract(value.multiply(denominator)),denominator);}
        public Fraction multiply(BigDecimal value) {return new Fraction(numerator.multiply(value),denominator);}
        public BigDecimal display(int scale,RoundingMode mode) {return numerator.divide(denominator,scale,mode);}
    }
    public static BigDecimal decimal(Object value) {return value==null?null:new BigDecimal(value.toString());}
    public static String amount(BigDecimal value) {return value==null?null:value.setScale(0,RoundingMode.DOWN).toPlainString();}
    public static String rate(Fraction value) {return value==null?null:value.display(1,RoundingMode.HALF_UP).toPlainString();}
    public static Fraction percent(BigDecimal n,BigDecimal d) {return n==null||d==null||d.signum()<=0?null:new Fraction(n.multiply(BigDecimal.valueOf(100)),d);}
    public static String apiMonth(String month) {return month.substring(0,4)+"-"+month.substring(4);}
    public static MissingReason adapt(MonthlyCostIssue issue,String metric) {
        return new MissingReason(issue.source(),issue.message(),apiMonth(issue.month()),issue.personId(),null,metric);
    }
    public Map<String,Object> row(Snapshot snapshot,Project project,YearMonth month) {
        var sources=snapshot.sources();String id=project.projectId();String end=month.toString().replace("-","");
        String projectStart=YearMonth.from(project.fromDate()).toString().replace("-","");
        String from=projectStart.compareTo(sources.period().baseMonth())<0?sources.period().baseMonth():projectStart;
        boolean before=projectStart.compareTo(sources.period().baseMonth())<0;
        List<MonthlyPersonCost> people=snapshot.costs().items().stream().filter(p->p.month().compareTo(from)>=0&&p.month().compareTo(end)<=0)
            .filter(p->p.rows().stream().anyMatch(r->id.equals(r.projectId()))||sources.assignments().stream()
                .anyMatch(a->id.equals(a.projectId())&&p.personId().equals(a.personId())&&p.month().equals(a.month()))).toList();
        var rows=people.stream().flatMap(p->p.rows().stream()).filter(r->id.equals(r.projectId())&&!Set.of("IDLE","INDRCT").contains(r.costClass())).toList();
        boolean costComplete=!before&&people.stream().allMatch(p->p.completeness().costComplete())&&rows.stream().allMatch(MonthlyCostRow::costComplete);
        boolean revenueComplete=people.stream().allMatch(p->p.completeness().revenueComplete())&&rows.stream().allMatch(MonthlyCostRow::revenueComplete);
        List<MissingReason> issues=new ArrayList<>();people.forEach(p->p.completeness().issues().forEach(i->issues.add(adapt(i,"cumulativeCost"))));
        if(before)issues.add(new MissingReason("CASH_BASE_YM","운영 시작월 이전 개별 원가가 없어 이관손익을 배분하지 않습니다.",apiMonth(sources.period().baseMonth()),null,null,"cumulativeCost"));
        boolean missingRows=sources.assignments().stream().filter(a->id.equals(a.projectId())&&a.month().compareTo(from)>=0&&a.month().compareTo(end)<=0)
            .anyMatch(a->rows.stream().noneMatch(r->a.month().equals(r.month())&&a.personId().equals(r.personId())&&a.assignmentId().equals(r.assignmentId())));
        if(missingRows){costComplete=false;revenueComplete=false;}
        BigDecimal extra=snapshot.expenses().stream().filter(e->id.equals(e.projectId())&&e.month().compareTo(from)>=0&&e.month().compareTo(end)<=0)
            .map(Expense::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal cost=costComplete?sum(rows,MonthlyCostRow::totalCost):null;if(cost!=null)cost=cost.add(extra);
        BigDecimal mm=sum(rows,MonthlyCostRow::assignmentMm),free=sum(rows,MonthlyCostRow::freeMm);
        if(mm==null)mm=BigDecimal.ZERO;if(free==null)free=BigDecimal.ZERO;
        BigDecimal freeCost=costComplete?sum(rows.stream().filter(r->"FREE".equals(r.inputType())).toList(),MonthlyCostRow::laborAllocated):null;
        boolean internal="IN".equals(project.contractType()),tk="TK".equals(project.contractType());
        BigDecimal budget=project.budget(),contract=project.contractAmount();
        BigDecimal revenue=null;
        if(!internal) {
            if(tk) {
                if(project.toDate().isBefore(month.atEndOfMonth()))revenue=contract;
                else if(costComplete&&budget!=null&&budget.signum()>0&&contract!=null)revenue=cost.compareTo(budget)>=0?contract:contract.multiply(cost).divide(budget,0,RoundingMode.DOWN);
                revenueComplete=revenue!=null;
            } else if(revenueComplete)revenue=sum(rows.stream().filter(r->!"FREE".equals(r.inputType())).toList(),MonthlyCostRow::personalRevenue);
        }
        if(!internal&&revenue==null)revenueComplete=false;
        BigDecimal profit=cost==null||revenue==null?null:revenue.subtract(cost);
        Fraction margin=percent(profit,revenue),usage=internal?null:percent(cost,budget);
        BigDecimal target=standard(sources,"TGT_MRGN_RT",end);if(target!=null&&(target.signum()<0||target.compareTo(BigDecimal.valueOf(100))>0))target=null;
        Fraction forecast=null,monthly=null;
        if(!internal&&!tk) {
            var selected=rows.stream().filter(r->end.equals(r.month())).toList();
            BigDecimal mc=sum(selected,MonthlyCostRow::totalCost),mr=sum(selected.stream().filter(r->!"FREE".equals(r.inputType())).toList(),MonthlyCostRow::personalRevenue);
            if(mc!=null)mc=mc.add(snapshot.expenses().stream().filter(e->id.equals(e.projectId())&&end.equals(e.month())).map(Expense::amount).reduce(BigDecimal.ZERO,BigDecimal::add));
            boolean complete=people.stream().filter(p->end.equals(p.month())).allMatch(p->p.completeness().costComplete()&&p.completeness().revenueComplete());
            monthly=complete&&mc!=null&&mr!=null?percent(mr.subtract(mc),mr):null;
        }
        if(tk&&"ING".equals(project.status())&&!project.toDate().isBefore(month.atEndOfMonth())&&cost!=null&&contract!=null&&contract.signum()>0) {
            BigDecimal remaining=forecast(snapshot,project,month,issues);
            if(remaining!=null)forecast=percent(contract.subtract(cost).subtract(remaining),contract);
        }
        Fraction comparison=tk?forecast:monthly;
        Fraction gap=comparison==null||target==null?null:comparison.subtract(target);
        List<Map<String,Object>> alerts=new ArrayList<>();
        BigDecimal low=standard(sources,"LOW_MRGN_GAP",end),warn=standard(sources,"BDGT_WARN_RT",end);
        if(!internal&&gap!=null&&low!=null&&low.signum()>=0&&gap.numerator().compareTo(low.negate().multiply(gap.denominator()))<0)
            alerts.add(fields("kind","LOW_MARGIN","message","저마진","actualNumerator",amount(comparison.numerator()),"actualDenominator",amount(comparison.denominator()),
                "threshold",low.toPlainString(),"comparison","(마진 분자 − 목표율 × 분모) < −경고폭 × 분모","primaryCause","목표 대비 마진 하락"));
        if(!internal&&usage!=null&&cost.compareTo(budget)>=0)alerts.add(fields("kind","BUDGET_OVER","message","예산 초과","actualNumerator",amount(cost),"actualDenominator",amount(budget),
            "threshold","100","comparison","누적 원가 >= 예산","primaryCause","누적 원가 예산 초과"));
        Boolean budgetWarning=usage==null||warn==null||warn.signum()<0?null:cost.compareTo(budget)<0&&cost.multiply(BigDecimal.valueOf(100)).compareTo(warn.multiply(budget))>=0;
        BigDecimal price=internal||revenue==null||mm.signum()==0?null:revenue.divide(mm,0,RoundingMode.DOWN);
        BigDecimal unitCost=cost==null||mm.signum()==0?null:cost.divide(mm,0,RoundingMode.DOWN);
        var evidence=snapshot.evidence().stream().filter(e->id.equals(e.projectId())).findFirst().orElse(null);
        Completeness completeness=new Completeness(costComplete,internal||revenueComplete,people.stream().anyMatch(p->p.completeness().estimated()),List.copyOf(issues),
            costComplete&&(internal||revenueComplete)?0:1,apiMonth(from),month.toString(),before?apiMonth(sources.period().baseMonth()):null);
        return fields("projectId",id,"projectName",project.name(),"contractType",project.contractType(),"status",project.toDate().isBefore(month.atEndOfMonth())?"END":"ING",
            "originalContractAmount",internal?null:amount(evidence==null?null:evidence.originalAmount()),"contractAmount",internal?null:amount(contract),
            "planMm",evidence==null||evidence.planMm()==null?null:evidence.planMm().setScale(2).toPlainString(),"actualMm",mm.setScale(2).toPlainString(),
            "cumulativeRevenue",amount(revenue),"cumulativeCost",amount(cost),"cumulativeProfit",amount(profit),"cumulativeMargin",rate(margin),"forecastMargin",rate(forecast),
            "monthlyMargin",rate(monthly),"targetMargin",internal||target==null?null:target.setScale(1,RoundingMode.HALF_UP).toPlainString(),"targetGap",rate(gap),
            "budgetAmount",internal?null:amount(budget),"budgetUsage",rate(usage),"pricePerMm",amount(price),"costPerMm",internal?null:amount(unitCost),
            "profitPerMm",price==null||unitCost==null?null:amount(price.subtract(unitCost)),"freeMm",free.setScale(2).toPlainString(),"freeLaborCost",internal?null:amount(freeCost),
            "alertCount",alerts.size(),"alerts",List.copyOf(alerts),"budgetWarning",internal?null:budgetWarning,"primaryCause",alerts.isEmpty()?null:alerts.get(0).get("primaryCause"),"completeness",completeness);
    }
    private BigDecimal forecast(Snapshot snapshot,Project project,YearMonth selected,List<MissingReason> issues) {
        var byPerson=new TreeMap<String,List<ForecastMonth>>();snapshot.future().stream().filter(f->project.projectId().equals(f.projectId())&&f.mm().signum()>0)
            .forEach(f->byPerson.computeIfAbsent(f.personId(),k->new ArrayList<>()).add(f));
        BigDecimal total=BigDecimal.ZERO;boolean complete=true;String end=selected.toString().replace("-","");
        for(var entry:byPerson.entrySet()) {
            String person=entry.getKey();BigDecimal mm=entry.getValue().stream().map(ForecastMonth::mm).reduce(BigDecimal.ZERO,BigDecimal::add);
            var latest=snapshot.costs().items().stream().filter(p->person.equals(p.personId())&&p.month().compareTo(end)<=0&&p.completeness().costComplete()&&p.laborSource().laborCost()!=null)
                .max(Comparator.comparing(MonthlyPersonCost::month));
            Fraction unit=latest.map(p->new Fraction(new BigDecimal(p.laborSource().laborCost()),BigDecimal.ONE)).orElse(null);
            String first=entry.getValue().stream().map(ForecastMonth::month).min(String::compareTo).orElseThrow().replace("-","");
            if(unit==null) {
                var price=snapshot.sources().prices().stream().filter(p->person.equals(p.personId())&&p.effective(first)&&p.monthlyPay()!=null)
                    .max(Comparator.comparing(Price::fromMonth));
                if(price.isPresent())unit=new Fraction(price.get().monthlyPay(),BigDecimal.ONE);
            }
            if(unit==null) {
                BigDecimal remaining=turnkey.selected(snapshot.sources(),project.projectId()).stream().filter(c->person.equals(c.developerId())&&"DEV".equals(c.rowType())&&c.month().compareTo(end)>0)
                    .map(c->c.amount()).reduce(BigDecimal.ZERO,BigDecimal::add);
                boolean present=turnkey.selected(snapshot.sources(),project.projectId()).stream().anyMatch(c->person.equals(c.developerId())&&"DEV".equals(c.rowType())&&c.month().compareTo(end)>0);
                BigDecimal budget=project.budget(),v=project.contractAmount();
                if(budget==null) {BigDecimal target=standard(snapshot.sources(),"TGT_MRGN_RT",end);if(target!=null&&target.signum()>=0&&target.compareTo(BigDecimal.valueOf(100))<=0&&v!=null)budget=v.multiply(BigDecimal.valueOf(100).subtract(target)).divide(BigDecimal.valueOf(100));}
                if(present&&budget!=null&&budget.signum()>0&&v!=null&&v.signum()>0)unit=new Fraction(remaining.multiply(budget),mm.multiply(v));
            }
            if(unit==null) {complete=false;issues.add(new MissingReason("UNIT_COST","단가 없음: 최근 완전한 월인건비·기본지급·배분 근거를 확인하세요.",selected.toString(),person,null,"forecastMargin"));}
            else total=total.add(unit.multiply(mm).display(0,RoundingMode.DOWN));
        }
        return complete?total:null;
    }
    public static BigDecimal standard(Sources sources,String code,String month) {return sources.standards().stream().filter(s->code.equals(s.code())&&s.fromMonth().compareTo(month)<=0)
        .max(Comparator.comparing(Standard::fromMonth)).map(Standard::value).orElse(null);}
    public static BigDecimal sum(List<MonthlyCostRow> rows,Function<MonthlyCostRow,String> getter) {
        BigDecimal total=BigDecimal.ZERO;for(var row:rows){String value=getter.apply(row);if(value==null)return null;total=total.add(new BigDecimal(value));}return total;
    }
}
