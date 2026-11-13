package com.sicost.costanalysis;

import com.sicost.profitability.MonthlyCostDtos.*;
import com.sicost.profitability.TurnkeyRevenueCalculation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import static com.sicost.costanalysis.ProjectCostDtos.*;
import static com.sicost.costanalysis.ProjectCostRepository.*;
import static com.sicost.profitability.MonthlyCostDtos.parseMonth;

@Component
public class ProjectCostCalculation {
    public static final String REVENUE_NOTICE="누적 매출·월별 배분 기준 매출(참고)·개인 단가·청구매출은 서로 다른 지표입니다. 원천을 수정하여 맞추지 않습니다.";
    private static final BigDecimal HUNDRED=new BigDecimal("100");
    private final TurnkeyRevenueCalculation turnkey;
    public ProjectCostCalculation(TurnkeyRevenueCalculation turnkey) {this.turnkey=turnkey;}
    public record View(Snapshot snapshot,String month,String start,List<MonthlyCostRow> rows,Completeness completeness) {}
    public View view(Snapshot s) {
        String end=s.sources().period().toMonth();
        String projectStart=YearMonth.from(s.project().startDate()).toString().replace("-","");
        String start=projectStart.compareTo(s.sources().period().baseMonth())<0?s.sources().period().baseMonth():projectStart;
        var rows=s.costs().items().stream().flatMap(p->p.rows().stream()).filter(r->s.project().id().equals(r.projectId())
            &&r.month().compareTo(start)>=0&&r.month().compareTo(end)<=0&&!Set.of("IDLE","INDRCT").contains(r.costClass())).toList();
        List<MissingReason> issues=new ArrayList<>();
        boolean cost=true,revenue=true,estimated=rows.stream().anyMatch(MonthlyCostRow::estimated);
        if(projectStart.compareTo(start)<0) {
            issues.add(new MissingReason("CASH_BASE_YM","운영 시작 전 개별 원가가 없습니다. 회사 이관손익은 배분하지 않습니다.",api(projectStart),null,null,"cumulativeCost"));
            cost=false;revenue=false;
        }
        for(var person:s.costs().items()) {
            boolean related=rows.stream().anyMatch(r->r.personId().equals(person.personId())&&r.month().equals(person.month()))
                ||s.sources().assignments().stream().anyMatch(a->s.project().id().equals(a.projectId())&&a.personId().equals(person.personId())&&a.month().equals(person.month())&&!"CNCL".equals(a.status()));
            if(!related||person.month().compareTo(start)<0||person.month().compareTo(end)>0)continue;
            cost &= person.completeness().costComplete();
            revenue &= person.completeness().revenueComplete();
            estimated |= person.completeness().estimated();
            for(var issue:person.completeness().issues()) issues.add(adapt(issue,!person.completeness().costComplete()?"cumulativeCost":"cumulativeRevenue"));
        }
        cost &= rows.stream().allMatch(MonthlyCostRow::costComplete);
        revenue &= rows.stream().allMatch(MonthlyCostRow::revenueComplete);
        for(var a:s.sources().assignments()) {
            if(!s.project().id().equals(a.projectId())||"CNCL".equals(a.status())||a.month().compareTo(start)<0||a.month().compareTo(end)>0)continue;
            if(rows.stream().noneMatch(r->r.key().equals(new CostKey(a.month(),a.personId(),a.projectId(),a.assignmentId())))) {
                cost=false;revenue=false;
                issues.add(new MissingReason("TB_CO_MON_COST","월 투입의 원가 원천이 없습니다.",api(a.month()),a.personId(),a.assignmentId(),"cumulativeCost"));
            }
        }
        var complete=new Completeness(cost,revenue,estimated,List.copyOf(issues),cost&&revenue?0:1,api(start),api(end),projectStart.compareTo(start)<0?api(start):null);
        return new View(s,api(end),start,rows,complete);
    }
    public static MissingReason adapt(MonthlyCostIssue issue,String metric) {
        return new MissingReason(issue.source(),issue.message(),issue.month()==null?null:api(issue.month()),issue.personId(),null,metric);
    }
    public Months months(View v) {
        List<MonthlyRow> result=new ArrayList<>();
        for(YearMonth ym=parseMonth(v.start());!ym.isAfter(YearMonth.parse(v.month()));ym=ym.plusMonths(1)) {
            String key=ym.toString().replace("-","");
            var rows=v.rows().stream().filter(r->key.equals(r.month())).toList();
            var comp=monthlyCompleteness(v,key,rows);
            BigDecimal labor=sum(rows,MonthlyCostRow::laborAllocated),direct=sum(rows,MonthlyCostRow::directExpense);
            BigDecimal extra=v.snapshot().expenses().stream().filter(e->key.equals(e.month())).map(ProjectCostMapper.Expense::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal cost=comp.costComplete()?add(labor,direct,extra):null;
            BigDecimal revenue=comp.revenueComplete()?paidRevenue(rows):null;
            BigDecimal reference="TK".equals(v.snapshot().project().contractType())?reference(v,key,cost):null;
            if("PRE".equals(v.snapshot().project().status()))revenue=BigDecimal.ZERO;
            if("IN".equals(v.snapshot().project().contractType()))revenue=null;
            result.add(new MonthlyRow(ym.toString(),mm(sum(rows,MonthlyCostRow::assignmentMm)),money(labor),money(sum(rows,MonthlyCostRow::teamExpense)),
                money(sum(rows,MonthlyCostRow::projectExpense)),money(direct),money(extra),money(cost),money(revenue),money(reference),
                margin(reference==null?revenue:reference,cost),rows.stream().anyMatch(r->"Y".equals(r.fixed())),comp));
        }
        return new Months(v.snapshot().project().id(),v.month(),List.copyOf(result),
            v.completeness().costComplete()?money(sumMonths(result,MonthlyRow::totalCost)):null,v.completeness());
    }
    private Completeness monthlyCompleteness(View v,String key,List<MonthlyCostRow> rows) {
        var issues=v.completeness().issues().stream().filter(i->api(key).equals(i.month())).toList();
        boolean cost=rows.stream().allMatch(MonthlyCostRow::costComplete),revenue=rows.stream().allMatch(MonthlyCostRow::revenueComplete);
        for(var person:v.snapshot().costs().items()) if(key.equals(person.month())&&
            (rows.stream().anyMatch(r->r.personId().equals(person.personId()))||v.snapshot().sources().assignments().stream().anyMatch(a->v.snapshot().project().id().equals(a.projectId())&&key.equals(a.month())&&a.personId().equals(person.personId())&&!"CNCL".equals(a.status())))) {
            cost &=person.completeness().costComplete();revenue &=person.completeness().revenueComplete();
        }
        if(issues.stream().anyMatch(i->i.code().equals("TB_CO_MON_COST")&&i.assignmentId()!=null)) {cost=false;revenue=false;}
        return new Completeness(cost,revenue,rows.stream().anyMatch(MonthlyCostRow::estimated),issues,cost&&revenue?0:1,api(key),api(key),null);
    }
    private BigDecimal reference(View v,String key,BigDecimal cost) {
        var selected=turnkey.selected(v.snapshot().sources(),v.snapshot().project().id());
        if(!selected.isEmpty())return selected.stream().filter(c->key.equals(c.month())&&Set.of("DEV","ITEM").contains(c.rowType())).map(c->c.amount()).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal budget=number(v.snapshot().project().budgetAmount());
        return cost!=null&&positive(budget)?cost.multiply(number(v.snapshot().project().contractAmount())).divide(budget,0,RoundingMode.DOWN):null;
    }
    public Assignments assignments(View v) {
        var s=v.snapshot();
        List<AssignmentRow> result=new ArrayList<>();
        for(var row:v.rows()) {
            var person=s.sources().people().stream().filter(p->p.personId().equals(row.personId())).findFirst().orElse(null);
            var a=s.sources().assignments().stream().filter(x->x.month().equals(row.month())&&x.assignmentId().equals(row.assignmentId())&&x.personId().equals(row.personId())&&x.projectId().equals(row.projectId())).findFirst().orElse(null);
            String job=Objects.toString(row.assignmentJobCode(),a==null?"":Objects.toString(a.jobCode(),""));
            result.add(new AssignmentRow(api(row.month()),row.assignmentId(),row.personId(),person==null?row.personId():person.name(),person==null?null:person.managementNo(),job,
                s.jobs().getOrDefault(job,job),a==null?s.project().startDate():a.fromDate(),a==null?s.project().endDate():a.toDate(),row.assignmentMm(),row.freeMm(),row.laborAllocated(),row.teamExpense(),row.projectExpense(),row.directExpense(),row.personalRevenue(),
                Objects.toString(row.inputType(),""),a==null?"":Objects.toString(a.status(),""),row.estimated(),"Y".equals(row.fixed()),monthlyCompleteness(v,row.month(),List.of(row))));
        }
        result.sort(Comparator.comparing(AssignmentRow::month).thenComparing(AssignmentRow::personId).thenComparing(AssignmentRow::assignmentId));
        return new Assignments(s.project().id(),v.month(),List.copyOf(result),v.completeness());
    }
    public Forecast forecast(View v) {
        var s=v.snapshot();var p=s.project();
        if(!"TK".equals(p.contractType())||"PRE".equals(p.status())||"LOSE".equals(p.status())||p.endDate().isBefore(YearMonth.parse(v.month()).atEndOfMonth()))return null;
        Map<String,List<FutureMonth>> grouped=new TreeMap<>();
        s.future().stream().filter(f->f.mm().signum()>0).forEach(f->grouped.computeIfAbsent(f.personId(),k->new ArrayList<>()).add(f));
        List<ForecastPerson> people=new ArrayList<>();List<MissingReason> issues=new ArrayList<>();
        BigDecimal total=BigDecimal.ZERO,totalMm=BigDecimal.ZERO;int missing=0;
        for(var entry:grouped.entrySet()) {
            String id=entry.getKey();var future=entry.getValue();
            BigDecimal remaining=future.stream().map(FutureMonth::mm).reduce(BigDecimal.ZERO,BigDecimal::add);totalMm=totalMm.add(remaining);
            var person=s.sources().people().stream().filter(x->id.equals(x.personId())).findFirst().orElse(null);
            var latest=s.costs().items().stream().filter(x->id.equals(x.personId())&&x.month().compareTo(v.month().replace("-",""))<=0&&x.completeness().costComplete()&&x.laborSource()!=null&&x.laborSource().laborCost()!=null)
                .filter(x->Set.of("FT","CT").contains(x.laborSource().employeeType())||s.sources().assignments().stream().anyMatch(a->id.equals(a.personId())&&a.month().equals(v.month().replace("-",""))&&a.assignmentMm().signum()>0))
                .max(Comparator.comparing(MonthlyPersonCost::month)).orElse(null);
            BigDecimal numerator=null,denominator=BigDecimal.ONE;String source="MISSING",sourceMonth=null;
            if(latest!=null) {numerator=number(latest.laborSource().laborCost());source="MONTHLY_COST";sourceMonth=api(latest.month());}
            String first=future.stream().map(FutureMonth::month).min(String::compareTo).orElseThrow().replace("-","");
            if(numerator==null) {
                var price=s.prices().stream().filter(x->id.equals(x.personId())&&x.effective(first)&&x.monthlyPay()!=null).max(Comparator.comparing(Price::fromMonth)).orElse(null);
                if(price!=null) {numerator=price.monthlyPay();source="BASE_PAY";sourceMonth=api(first);}
            }
            if(numerator==null) {
                BigDecimal budget=number(p.budgetAmount()),value=number(p.contractAmount());
                BigDecimal target=standard(s,"TGT_MRGN_RT",v.month().replace("-",""));
                if(budget==null&&validTarget(target)&&value!=null)budget=value.multiply(HUNDRED.subtract(target)).divide(HUNDRED,0,RoundingMode.DOWN);
                Set<String> keys=new HashSet<>();future.forEach(f->keys.add(f.month().replace("-","")));
                var cells=turnkey.selected(s.sources(),p.id()).stream().filter(c->"DEV".equals(c.rowType())&&id.equals(c.developerId())&&keys.contains(c.month())).toList();
                if(!cells.isEmpty()&&positive(value)&&positive(budget)) {
                    numerator=cells.stream().map(c->c.amount()).reduce(BigDecimal.ZERO,BigDecimal::add).multiply(budget);
                    denominator=remaining.multiply(value);source="DISTRIBUTION";sourceMonth=v.month();
                }
            }
            List<MissingReason> personIssues=new ArrayList<>();
            if(numerator==null) {missing++;personIssues.add(new MissingReason("UNIT_COST_MISSING","단가 없음: 최근 월인건비·기본 지급액·배분표를 확인해 주세요.",v.month(),id,null,"forecastMargin"));issues.addAll(personIssues);}
            BigDecimal contribution=numerator==null?BigDecimal.ZERO:remaining.multiply(numerator).divide(denominator,0,RoundingMode.DOWN);total=total.add(contribution);
            // Scale both sides equally so the public rational components remain integer strings.
            int scale=Math.max(0,Math.max(numerator==null?0:numerator.scale(),denominator.scale()));
            people.add(new ForecastPerson(id,person==null?id:person.name(),mm(remaining),source,sourceMonth,money(numerator==null?null:numerator.movePointRight(scale)),money(numerator==null?null:denominator.movePointRight(scale)),money(contribution),List.copyOf(personIssues)));
        }
        return new Forecast(v.month(),mm(totalMm),people.size(),missing,money(total),missing==0,List.copyOf(people),List.copyOf(issues));
    }
    public Detail detail(View v) {
        var s=v.snapshot();var p=s.project();var months=months(v);var last=months.items().stream().filter(m->v.month().equals(m.month())).findFirst().orElse(null);
        boolean internal="IN".equals(p.contractType()),pre="PRE".equals(p.status()),ended=p.endDate().isBefore(YearMonth.parse(v.month()).atEndOfMonth());
        BigDecimal cost=number(months.totalCost()),budget=number(p.budgetAmount()),value=number(p.contractAmount());
        BigDecimal revenue;
        if(internal)revenue=null;
        else if(pre)revenue=BigDecimal.ZERO;
        else if("TK".equals(p.contractType()))revenue=ended?value:cost!=null&&positive(budget)?value.multiply(cost.min(budget)).divide(budget,0,RoundingMode.DOWN):null;
        else revenue=v.completeness().revenueComplete()?paidRevenue(v.rows()):null;
        BigDecimal profit=revenue==null||cost==null?null:revenue.subtract(cost);
        BigDecimal actualMm=sum(v.rows(),MonthlyCostRow::assignmentMm),freeMm=sum(v.rows(),MonthlyCostRow::freeMm);
        BigDecimal freeCost=sum(v.rows().stream().filter(r->positive(number(r.freeMm()))).toList(),MonthlyCostRow::laborAllocated);
        Forecast forecast=forecast(v);
        BigDecimal forecastRevenue=forecast==null?null:value,forecastCost=forecast==null||!forecast.complete()||cost==null?null:cost.add(number(forecast.knownRemainingCost()));
        String forecastMargin=margin(forecastRevenue,forecastCost);
        String monthlyMargin=internal||pre||"TK".equals(p.contractType())||last==null?null:margin(number(last.revenue()),number(last.totalCost()));
        BigDecimal target=standard(s,"TGT_MRGN_RT",v.month().replace("-",""));if(!validTarget(target)||internal||pre)target=null;
        BigDecimal comparisonRevenue="TK".equals(p.contractType())?forecastRevenue:last==null?null:number(last.revenue());
        BigDecimal comparisonCost="TK".equals(p.contractType())?forecastCost:last==null?null:number(last.totalCost());
        BigDecimal low=standard(s,"LOW_MRGN_GAP",v.month().replace("-","")),warn=standard(s,"BDGT_WARN_RT",v.month().replace("-",""));
        List<Alert> alerts=new ArrayList<>();Boolean warning=null;
        if(!internal&&!pre) {
            if(positive(budget)&&cost!=null) {
                if(cost.compareTo(budget)>=0)alerts.add(new Alert("BUDGET_OVER","예산 초과",money(cost),money(budget),"100","C >= B",null));
                warning=warn!=null&&warn.signum()>=0?cost.compareTo(budget)<0&&cost.multiply(HUNDRED).compareTo(budget.multiply(warn))>=0:null;
            }
            if(target!=null&&low!=null&&low.signum()>=0&&positive(comparisonRevenue)&&comparisonCost!=null
                &&comparisonRevenue.subtract(comparisonCost).multiply(HUNDRED).compareTo(comparisonRevenue.multiply(target.subtract(low)))<0)
                alerts.add(new Alert("LOW_MARGIN","저마진",money(comparisonRevenue.subtract(comparisonCost)),money(comparisonRevenue),low.toPlainString(),"(R-C)*100 < R*(목표-경고폭)",null));
        }
        BigDecimal price=positive(actualMm)&&revenue!=null?revenue.divide(actualMm,0,RoundingMode.DOWN):null;
        BigDecimal unitCost=positive(actualMm)&&cost!=null?cost.divide(actualMm,0,RoundingMode.DOWN):null;
        String selectedMargin="TK".equals(p.contractType())?forecastMargin:monthlyMargin;
        var row=new Row(p.id(),p.name(),p.contractType(),ended?"END":"ING",internal?null:p.originalContractAmount(),internal?null:p.contractAmount(),mm(s.planMm()),mm(actualMm),money(revenue),money(cost),money(profit),margin(revenue,cost),forecastMargin,monthlyMargin,rate(target),gap(selectedMargin,target),internal?null:p.budgetAmount(),internal?null:ratio(cost,budget),money(price),money(internal?null:unitCost),money(price==null||unitCost==null?null:price.subtract(unitCost)),mm(freeMm),money(freeCost),alerts.size(),List.copyOf(alerts),warning,null,v.completeness());
        boolean enabled=!internal&&!pre&&comparisonCost!=null&&positive(comparisonRevenue)&&(!"TK".equals(p.contractType())||forecast!=null&&forecast.complete());
        int m="TK".equals(p.contractType())?(int)Math.max(1,ChronoUnit.MONTHS.between(YearMonth.parse(v.month()),YearMonth.from(p.endDate()))):1;
        return new Detail(p.id(),v.month(),YearMonth.parse(v.month()).atEndOfMonth(),p.status(),row,forecast,
            new SimulationDefaults(enabled,enabled?null:"원가·매출·남은 단가가 완전한 계산 가능 프로젝트에서 사용할 수 있습니다.",1,inputMoney(unitCost),m,inputMoney(price)),REVENUE_NOTICE,v.completeness());
    }
    private static String inputMoney(BigDecimal value) {return value!=null&&value.signum()>=0&&value.compareTo(new BigDecimal("999999999999999"))<=0?money(value):null;}
    public Causes causes(View v) {
        var p=v.snapshot().project();var detail=detail(v);
        BigDecimal progress=v.snapshot().actualProgress()==null?BigDecimal.ZERO:v.snapshot().actualProgress();
        if("IN".equals(p.contractType())||"PRE".equals(p.status()))return new Causes(p.id(),v.month(),List.copyOf(Collections.emptyList()),rate(progress),null,null,false,false,"내부·선투입 프로젝트는 원가 초과 원인 분석 대상이 아닙니다.",v.completeness().issues());
        BigDecimal cost=number(detail.row().cumulativeCost()),mm=sum(v.rows(),MonthlyCostRow::assignmentMm),plan=v.snapshot().planMm();
        long total=ChronoUnit.MONTHS.between(YearMonth.from(p.startDate()),YearMonth.from(p.endDate()))+1;
        long elapsed=Math.max(0,Math.min(total,ChronoUnit.MONTHS.between(YearMonth.from(p.startDate()),YearMonth.parse(v.month()))+1));
        BigDecimal excessMm=cost!=null&&positive(mm)&&plan!=null?
            mm.multiply(BigDecimal.valueOf(total)).subtract(plan.multiply(BigDecimal.valueOf(elapsed))).max(BigDecimal.ZERO).multiply(cost).divide(mm.multiply(BigDecimal.valueOf(total)),0,RoundingMode.DOWN):null;
        List<MissingReason> mmIssues=excessMm==null?List.of(new MissingReason("TB_PJ_MNPW_PLAN",plan==null?"계획 없음":"실제 MM 또는 원가 미확인",v.month(),null,null,"EXCESS_MM")):List.copyOf(Collections.emptyList());
        BigDecimal over=BigDecimal.ZERO;List<MissingReason> overIssues=new ArrayList<>();
        for(var row:v.rows()) {
            if("FREE".equals(row.inputType())||positive(number(row.freeMm())))continue;
            BigDecimal standard=standard(v.snapshot(),"COST_OVER_UPRC_RT",row.month());
            if(row.laborAllocated()==null||row.personalRevenue()==null||standard==null||standard.signum()<0) {
                overIssues.add(new MissingReason("COST_OVER_UPRC_RT","노동비·행 개인단가 또는 월 기준율이 없습니다.",api(row.month()),row.personId(),row.assignmentId(),"OVER_UNIT_COST"));continue;
            }
            over=over.add(number(row.laborAllocated()).multiply(HUNDRED).subtract(number(row.personalRevenue()).multiply(standard)).max(BigDecimal.ZERO).divide(HUNDRED,0,RoundingMode.DOWN));
        }
        BigDecimal free=sum(v.rows().stream().filter(r->positive(number(r.freeMm()))).toList(),MonthlyCostRow::laborAllocated);
        List<Cause> items=new ArrayList<>(List.of(new Cause("EXCESS_MM",money(excessMm),"DOWN(max(0,실제MM-계획MM×경과/전체월)×C/실제MM)",mmIssues,"UI-01",p.id()),
            new Cause("OVER_UNIT_COST",overIssues.isEmpty()?money(over):null,"Σ DOWN(max(0,노동비-행개인단가×월기준율/100)); FREE 제외",List.copyOf(overIssues),"UI-32",p.id()),
            new Cause("FREE_SUPPORT",money(free),"기간 FREE_MM>0 행 노동비 합계",List.copyOf(Collections.emptyList()),"UI-04",p.id())));
        items.sort(Comparator.comparing((Cause c)->number(c.amount()),Comparator.nullsLast(Comparator.reverseOrder())));
        BigDecimal budget=number(p.budgetAmount());
        BigDecimal excess="TK".equals(p.contractType())&&cost!=null&&budget!=null?cost.multiply(HUNDRED).subtract(budget.multiply(progress)).divide(HUNDRED,0,RoundingMode.DOWN):null;
        BigDecimal remainder=excess!=null&&items.stream().allMatch(i->i.amount()!=null)?excess.subtract(items.stream().map(i->number(i.amount())).reduce(BigDecimal.ZERO,BigDecimal::add)):null;
        List<MissingReason> issues=new ArrayList<>(v.completeness().issues());issues.addAll(mmIssues);issues.addAll(overIssues);
        boolean highlight=!detail.row().alerts().isEmpty()||positive(excess);
        return new Causes(p.id(),v.month(),List.copyOf(items),rate(progress),money(excess),money(remainder),positive(remainder),highlight,
            positive(remainder)?"위 세 가지로 설명되지 않습니다":highlight?"세 원인은 중첩될 수 있으며 회계적 차액분해가 아닙니다.":"경보가 없는 프로젝트라 참고용",List.copyOf(issues));
    }
    public static BigDecimal standard(Snapshot s,String code,String month) {
        return s.sources().standards().stream().filter(x->code.equals(x.code())&&x.fromMonth().compareTo(month)<=0).max(Comparator.comparing(Standard::fromMonth)).map(Standard::value).orElse(null);
    }
    private static boolean validTarget(BigDecimal value) {return value!=null&&value.signum()>=0&&value.compareTo(HUNDRED)<=0;}
    public static BigDecimal number(String value) {return value==null?null:new BigDecimal(value);}
    public static boolean positive(BigDecimal value) {return value!=null&&value.signum()>0;}
    public static String money(BigDecimal value) {return value==null?null:value.setScale(0,RoundingMode.DOWN).toPlainString();}
    public static String mm(BigDecimal value) {return value==null?null:value.setScale(2,RoundingMode.UNNECESSARY).toPlainString();}
    public static String rate(BigDecimal value) {return value==null?null:value.setScale(1,RoundingMode.HALF_UP).toPlainString();}
    public static String ratio(BigDecimal numerator,BigDecimal denominator) {return numerator==null||!positive(denominator)?null:numerator.multiply(HUNDRED).divide(denominator,1,RoundingMode.HALF_UP).toPlainString();}
    public static String margin(BigDecimal revenue,BigDecimal cost) {return revenue==null||cost==null?null:ratio(revenue.subtract(cost),revenue);}
    public static String gap(String margin,BigDecimal target) {return margin==null||target==null?null:rate(number(margin).subtract(target));}
    private static BigDecimal add(BigDecimal... values) {BigDecimal result=BigDecimal.ZERO;for(var value:values){if(value==null)return null;result=result.add(value);}return result;}
    private static BigDecimal sum(List<MonthlyCostRow> rows,Function<MonthlyCostRow,String> field) {BigDecimal result=BigDecimal.ZERO;for(var row:rows){var n=number(field.apply(row));if(n==null)return null;result=result.add(n);}return result;}
    private static BigDecimal sumMonths(List<MonthlyRow> rows,Function<MonthlyRow,String> field) {BigDecimal result=BigDecimal.ZERO;for(var row:rows){var n=number(field.apply(row));if(n==null)return null;result=result.add(n);}return result;}
    private static BigDecimal paidRevenue(List<MonthlyCostRow> rows) {return sum(rows.stream().filter(r->!"FREE".equals(r.inputType())).toList(),MonthlyCostRow::personalRevenue);}
    public static String api(String month) {return month.substring(0,4)+"-"+month.substring(4);}
}
