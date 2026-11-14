package com.sicost.salespartsettlements;

import com.sicost.profitability.MonthlyCostDtos.*;
import com.sicost.profitability.MonthlyCostService;
import com.sicost.profitability.MonthlyCostCalculation;
import com.sicost.profitability.TurnkeyRevenueCalculation;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.sicost.salespartsettlements.SalesPartSettlementDtos.*;
import static com.sicost.salespartsettlements.SalesPartSettlementCalculator.*;

@Component
public class SalesPartSettlementSources {
    private final MonthlyCostService monthly;
    private final TurnkeyRevenueCalculation turnkey;
    public SalesPartSettlementSources(MonthlyCostService monthly, TurnkeyRevenueCalculation turnkey) {this.monthly=monthly;this.turnkey=turnkey;}
    public List<Row> calculate(SalesPartSettlementRepository.Snapshot snapshot, Tab tab, List<Issue> issues) {
        Sources sources=snapshot.sources();String month=sources.period().toMonth(), apiMonth=parse(month).toString();
        Map<String,MonthlyPersonCost> costs=new HashMap<>();
        monthly.calculate(sources).items().stream().filter(c->month.equals(c.month())).forEach(c->costs.put(c.personId(),c));
        Map<String,Project> projects=new HashMap<>();sources.projects().forEach(p->projects.put(p.projectId(),p));
        List<Row> result=new ArrayList<>();
        for(Person person:sources.people()) {
            if(!tab.partId().equals(person.personPartId()))continue;
            Payroll uploaded=sources.payroll().stream().filter(p->month.equals(p.month())&&person.personId().equals(p.personId())).findFirst().orElse(null);
            String type=uploaded==null?person.employeeType():uploaded.employeeType();
            String direct=uploaded==null?person.directClass():uploaded.directClass();
            if("INDRCT".equals(direct)||MonthlyCostCalculation.fullMonthLeave(sources.states(),person.personId(),month))continue;
            MonthlyPersonCost cost=costs.get(person.personId());
            List<MonthlyCostRow> inputs=cost==null?List.of():cost.rows().stream().filter(r->!MonthlyCostService.synthetic(r)&&!"IN".equals(r.contractType())&&r.assignmentMm()!=null&&decimal(r.assignmentMm()).signum()>0).toList();
            if(inputs.isEmpty())continue;
            boolean fixedElsewhere=snapshot.details().stream().anyMatch(d->d.developerId().equals(person.personId())&&!d.partId().equals(tab.partId())&&snapshot.parents().stream().anyMatch(p->p.partId().equals(d.partId())&&p.confirmed()));
            if(fixedElsewhere) {issues.add(issue("PERSON_ALREADY_CONFIRMED","다른 파트에 확정된 인력은 제외했습니다.","developerId",person.personId(),apiMonth,"TB_CO_PART_SETL_DTL",false));continue;}
            List<Issue> rowIssues=new ArrayList<>();
            LocalDate first=snapshot.firstInputs().stream().filter(f->f.developerId().equals(person.personId())).map(FirstInput::date).findFirst().orElse(null);
            YearMonth ym=parse(month);boolean start=first!=null&&YearMonth.from(first).equals(ym);
            List<Price> prices=sources.prices().stream().filter(p->p.personId().equals(person.personId())&&p.effective(month)).toList();
            BigDecimal base=prices.size()==1?prices.get(0).monthlyPay():null;
            int days=start?ym.lengthOfMonth()-first.getDayOfMonth()+1:ym.lengthOfMonth();
            boolean missingStart=start&&base==null&&prices.size()<=1;
            boolean estimated=!start&&uploaded==null;
            String paySource;
            BigDecimal pay;
            if(prices.size()>1&&(start||uploaded==null)) {
                pay=null;paySource="UNKNOWN";
                rowIssues.add(issue("PAY_STANDARD_OVERLAP","기본급 적용기간이 겹칩니다.","payAmount",person.personId(),apiMonth,"TB_HR_DEV_PRC",true));
            } else if(start) {
                pay=missingStart?BigDecimal.ZERO:base.multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(ym.lengthOfMonth()),0,RoundingMode.DOWN);
                paySource=missingStart?"START_MONTH_MISSING_ZERO":"START_MONTH_BASE";
            } else if(uploaded!=null) {pay="OS".equals(type)?uploaded.vendorSupply():uploaded.pay();paySource="OS".equals(type)?"VENDOR_SUPPLY":"UPLOADED_PAY";}
            else {pay=base;paySource=pay==null?"UNKNOWN":"ESTIMATED_BASE";}
            if(first==null) {pay=null;paySource="UNKNOWN";rowIssues.add(issue("FIRST_INPUT_UNKNOWN","최초 투입일을 확인할 수 없습니다.","payAmount",person.personId(),apiMonth,"TB_PJ_ASGN",true));}
            if(missingStart)rowIssues.add(issue("START_MONTH_MISSING_PAY","⚠ 월 급여 없음(시작 달 급여 0)","payAmount",person.personId(),apiMonth,"TB_HR_DEV_PRC",false));
            else if(pay==null)rowIssues.add(issue("PAY_UNKNOWN","월 급여 미확인", "payAmount",person.personId(),apiMonth,"TB_HR_MON_PAY",true));
            if(estimated&&pay!=null)rowIssues.add(issue("ESTIMATED_PAY","급여 미업로드·예정", "payAmount",person.personId(),apiMonth,"TB_HR_DEV_PRC",false));
            BigDecimal revenue=inputs.stream().anyMatch(r->r.personalRevenue()==null)?null:inputs.stream().map(r->decimal(r.personalRevenue())).reduce(BigDecimal.ZERO,BigDecimal::add);
            if(revenue==null)rowIssues.add(issue("UNIT_PRICE_UNKNOWN","단가 미확인: 귀속 매출 또는 TK 계산 근거를 확인해 주세요.","unitPrice",person.personId(),apiMonth,"월별 원가",true));
            BigDecimal mm=inputs.stream().map(r->decimal(r.assignmentMm())).reduce(BigDecimal.ZERO,BigDecimal::add);
            Map<String,BigDecimal> projectMm=new HashMap<>();inputs.forEach(r->projectMm.merge(r.projectId(),decimal(r.assignmentMm()),BigDecimal::add));
            List<String> projectIds=projectMm.keySet().stream().sorted(Comparator.<String,BigDecimal>comparing(projectMm::get).reversed().thenComparing(id->projects.containsKey(id)?projects.get(id).name():id).thenComparing(id->id)).toList();
            List<String> names=projectIds.stream().map(id->projects.containsKey(id)?projects.get(id).name():id).toList();
            List<String> tkSources=new ArrayList<>();
            for(String id:projectIds)if(projects.containsKey(id)&&"TK".equals(projects.get(id).contractType())) {
                var selected=turnkey.selected(sources,id);boolean hasPerson=selected.stream().anyMatch(c->"DEV".equals(c.rowType())&&person.personId().equals(c.developerId()));
                tkSources.add(id+": "+(hasPerson?selected.get(0).step():"노동비×계약금액/예산"));
            }
            Amounts amounts=amounts(type,pay,revenue,tab.standard());
            List<String> remarks=new ArrayList<>();remarks.add("FT".equals(type)?"공통관리비+퇴직금":"OS".equals(type)?"외주":"공통관리비");
            if(start)remarks.add("시작 "+days+"/"+ym.lengthOfMonth()+"일");
            if(inputs.stream().anyMatch(r->"FREE".equals(r.inputType())))remarks.add("무상지원");
            if(inputs.stream().anyMatch(r->"PRE".equals(r.costClass())))remarks.add("선투입");
            if(missingStart)remarks.add("월 급여 없음(시작 달 급여 0)");else if(estimated)remarks.add("급여 예정");
            if(!tkSources.isEmpty())remarks.add("턴키");
            for(var entry:Map.of("mm",mm.toPlainString(),"payAmount",Objects.toString(amounts.pay(),""),"unitPrice",Objects.toString(money(revenue),""),"costAmount",Objects.toString(amounts.cost(),""),"profitAmount",Objects.toString(amounts.profit(),""),"profitShareAmount",Objects.toString(amounts.share(),"")).entrySet()) {
                if(!entry.getValue().isEmpty()&&decimal(entry.getValue()).abs().compareTo(new BigDecimal(entry.getKey().equals("mm")?"9999.99":"999999999999999"))>0)
                    rowIssues.add(issue("AMOUNT_OUT_OF_RANGE","저장 허용 범위를 초과합니다.",entry.getKey(),person.personId(),apiMonth,"TB_CO_PART_SETL_DTL",true));
            }
            result.add(new Row(person.personId(),person.name(),person.managementNo(),type,names,null,mm.setScale(2).toPlainString(),money(revenue),amounts.pay(),amounts.expense(),amounts.retirement(),amounts.cost(),amounts.profit(),amounts.share(),String.join(" · ",remarks),
                new Basis(paySource,estimated,missingStart,first,ym.lengthOfMonth(),days,money(base),tab.standard().distributionRate(),List.copyOf(tkSources),List.copyOf(rowIssues)),List.copyOf(rowIssues)));
        }
        return List.copyOf(result);
    }
    private static YearMonth parse(String month) {return com.sicost.profitability.MonthlyCostDtos.parseMonth(month);}
}
