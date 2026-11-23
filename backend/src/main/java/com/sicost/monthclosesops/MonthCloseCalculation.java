package com.sicost.monthclosesops;

import com.sicost.cashflowops.CashFlowDtos;
import com.sicost.cashflowops.CashFlowService;
import com.sicost.businessplansops.*;
import com.sicost.common.assignments.AssignmentMonthlyService;
import com.sicost.corporatetaxesops.*;
import com.sicost.profitability.*;
import com.sicost.salespartsettlementsops.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.sicost.monthclosesops.MonthCloseDtos.*;
import static com.sicost.monthclosesops.MonthCloseRepository.*;
import static com.sicost.profitability.MonthlyCostDtos.*;
import com.sicost.monthclosesops.MonthCloseDtos.Part;

@Service
public class MonthCloseCalculation {
    private final MonthlyCostRepository monthly;
    private final MonthlyCostService costs;
    private final AssignmentMonthlyService assignments;
    private final BusinessPlanPerformanceMapper financial;
    private final BusinessPlanActuals actuals;
    private final CorporateTaxMapper taxes;
    private final CorporateTaxAllocationService allocation;
    private final SettlementClosingService settlements;
    private final CashFlowService cashFlow;
    public MonthCloseCalculation(MonthlyCostRepository monthly,MonthlyCostService costs,AssignmentMonthlyService assignments,
        BusinessPlanPerformanceMapper financial,BusinessPlanActuals actuals,CorporateTaxMapper taxes,CorporateTaxAllocationService allocation,
        SettlementClosingService settlements,CashFlowService cashFlow) {
        this.monthly=monthly;this.costs=costs;this.assignments=assignments;this.financial=financial;this.actuals=actuals;this.taxes=taxes;this.allocation=allocation;this.settlements=settlements;this.cashFlow=cashFlow;
    }
    public record Candidate(String table,Map<String,Object> row) {}
    public record Plan(Sources sources,List<Candidate> candidates,List<Part> parts,List<Issue> issues,boolean estimated,CashImpact cashImpact) {}
    public Sources sources(String month,Map<String,List<Map<String,Object>>> data) {
        Sources source=monthly.loadRange(month);
        String db=month.replace("-","");List<Assignment> selected=new ArrayList<>();
        for(var row:data.get("TB_PJ_ASGN")) {
            String id=s(row,"asgn_id");var original=assignments.required(id);
            var project=source.projects().stream().filter(p->p.projectId().equals(original.projectId())).findFirst().orElseThrow(()->conflict("STATE_CONFLICT","투입 프로젝트를 확인해 주세요."));
            var old=data.get("TB_PJ_ASGN_MON").stream().filter(m->id.equals(s(m,"asgn_id"))&&db.equals(s(m,"input_ym"))&&"Y".equals(s(m,"fix_yn"))).findFirst();
            var computed=old.isPresent()?assignments.mapper().months(id).stream().filter(m->db.equals(m.month())).map(m->assignments.view(original,m,assignments.mapper().histories(id))).toList():
                assignments.calculate(original,assignments.mapper().histories(id),project.status(),project.contractType());
            for(var m:computed)if(month.equals(m.month()))selected.add(new Assignment(db,m.developerId(),m.projectId(),id,new BigDecimal(m.monthlyMm()),decimal(m.appliedUnitPrice()),decimal(m.salesAmount()),m.fixed(),original.jobCode(),original.inputType(),original.contractDeveloperId(),original.status(),original.startDate(),original.endDate()));
        }
        var all=new ArrayList<>(source.assignments().stream().filter(a->!db.equals(a.month())).toList());all.addAll(selected);
        var projectMonths=new ArrayList<>(source.projectMonths().stream().filter(a->!db.equals(a.month())).toList());projectMonths.addAll(selected);
        var period=new ProfitabilityPeriod(source.period().year(),source.period().baseMonth(),db,db,source.period().calculatedAt(),false);
        return new Sources(period,source.actor(),source.asOfDate(),source.people(),source.prices(),source.states(),source.payroll(),List.copyOf(all),List.copyOf(projectMonths),source.projects(),source.projectParts(),source.turnkey(),source.storedCosts(),source.standards(),source.parts(),source.closes(),source.allowanceCodes());
    }
    public Plan plan(String month,Map<String,List<Map<String,Object>>> data) {
        Sources source=sources(month,data);String db=month.replace("-","");List<Candidate> candidates=new ArrayList<>();List<Issue> issues=new ArrayList<>();
        for(Assignment a:source.assignments())if(db.equals(a.month())&&!"Y".equals(a.fixed())) {
            int days=com.sicost.projectsops.ProjectAssignmentMonths.days(YearMonth.parse(month),a.fromDate(),a.toDate());
            candidates.add(new Candidate("TB_PJ_ASGN_MON",row("asgn_id",a.assignmentId(),"input_ym",db,"dev_id",a.personId(),"proj_id",a.projectId(),"input_day_cnt",days,"mon_mm",a.assignmentMm(),"aply_uprc",a.appliedUnitPrice(),"sales_amt",a.salesAmount(),"fix_yn","Y")));
        }
        var people=costs.calculate(source).items().stream().filter(p->db.equals(p.month())).toList();
        for(var person:people) {
            if(person.rows().isEmpty()&&!person.completeness().allocationComplete()) {
                issues.add(new Issue("AMOUNT_UNCONFIRMED",
                    personName(source,person.personId())+"의 "+month
                        +" 원가 금액이 없어 마감할 수 없습니다. 개인 원가·수익표에서 확인해 주세요.",
                    month,"TB_CO_MON_COST",person.personId(),"amount"));
            }
            for(var r:person.rows())if(!"Y".equals(r.fixed()))candidates.add(new Candidate("TB_CO_MON_COST",row("cost_ym",db,"dev_id",r.personId(),"proj_id",r.projectId(),"asgn_id",r.assignmentId(),"asgn_mm",r.assignmentMm(),"free_mm",r.freeMm(),"sales_amt",r.personalRevenue(),"labor_alloc_amt",r.laborAllocated(),"team_exps_amt",r.teamExpense(),"proj_exps_amt",r.projectExpense(),"drct_exps_amt",r.directExpense(),"cost_cls_cd",r.costClass(),"ctrt_type_cd",r.contractType(),"part_id",r.projectSalesPartId(),"fix_yn","Y")));
        }
        String year=month.substring(0,4);
        var allocated=allocation.calculate(year,taxes.annual(year),YearMonth.parse(api(source.period().baseMonth())),taxes.months(year));
        String tax=allocated.months().stream().filter(m->month.equals(m.month())).map(m->m.amount()).filter(Objects::nonNull).findFirst().orElse(null);
        var pl=actuals.calculated(month,new BusinessPlanActuals.OperatingSources(people,financial.financialSources(new BusinessPlanPerformanceDtos.YearRange(db,db)),source),tax,source.period().calculatedAt());
        Map<String,Object> plRow=row("pl_ym",db,"fix_yn","Y","migr_yn","N");
        BusinessPlanActuals.AMOUNTS.forEach(k->plRow.put(k.replaceAll("([a-z])([A-Z])","$1_$2").toLowerCase(Locale.ROOT),pl.amounts().get(k)));
        candidates.add(new Candidate("TB_CO_MON_PL",plRow));
        List<Part> parts=new ArrayList<>();
        var tabs=settlements.listEligibleParts(month);
        tabs.issues().stream().filter(i->i.blocking()).forEach(i->issues.add(new Issue(i.code(),i.message(),month,"TB_BS_PART_STD",null,i.field())));
        for(var tab:tabs.parts())if(tab.eligible()) {
            List<Issue> problems=new ArrayList<>();boolean pending="WORK".equals(tab.status());
            if(pending) {
                var preview=settlements.previewSettlement(tab.partId(),month);
                preview.issues().stream().filter(i->i.blocking()).forEach(i->problems.add(new Issue(i.code(),
                    tab.partName()+"의 "+month+" 정산에 필요한 금액 또는 기준을 확인할 수 없습니다. "
                        +(i.developerId()==null?"":personName(source,i.developerId())+"의 자료를 ")
                        +"영업 파트 이익금에서 확인해 주세요.",month,"TB_CO_PART_SETL",tab.partId(),i.field())));
                if(!preview.canConfirm()&&problems.isEmpty())problems.add(new Issue("STATE_CONFLICT",
                    tab.partName()+"의 "+month+" 정산에 필요한 금액 또는 기준을 확인할 수 없습니다. 영업 파트 이익금에서 확인해 주세요.",
                    month,"TB_CO_PART_SETL",tab.partId(),null));
            }
            issues.addAll(problems);parts.add(new Part(tab.partId(),tab.partName(),tab.status(),pending,List.copyOf(problems)));
        }
        for(Candidate candidate:candidates) {
            if(candidate.table().equals("TB_CO_MON_PL")&&data.get(candidate.table()).stream().anyMatch(r->db.equals(s(r,"pl_ym"))&&("Y".equals(s(r,"fix_yn"))||"Y".equals(s(r,"migr_yn")))))
                issues.add(new Issue("FIXED_DATA_CONFLICT",month+" 손익은 이미 고정되었거나 이관된 자료라 마감할 수 없습니다. 월별 손익에서 확인해 주세요.",month,candidate.table(),db,"FIX_YN"));
            for(var value:candidate.row().entrySet())if(value.getKey().endsWith("_amt")||value.getKey().endsWith("_mm")||value.getKey().equals("aply_uprc")) {
                boolean nullable=candidate.table().equals("TB_PJ_ASGN_MON")&&value.getKey().equals("aply_uprc");
                if(value.getValue()==null) {if(!nullable)issues.add(new Issue("AMOUNT_UNCONFIRMED",
                    candidateProblem(month,candidate,source,false),month,candidate.table(),key(candidate.table(),candidate.row()),value.getKey()));}
                else try{range(new BigDecimal(value.getValue().toString()),value.getKey().endsWith("_mm")?6:15,value.getKey().endsWith("_mm")?2:0);}
                catch(ArithmeticException e){issues.add(new Issue("STATE_CONFLICT",candidateProblem(month,candidate,source,true),month,candidate.table(),key(candidate.table(),candidate.row()),value.getKey()));}
            }
        }
        return new Plan(source,List.copyOf(candidates),List.copyOf(parts),List.copyOf(issues),people.stream().anyMatch(p->p.completeness().estimated()),cashImpact(month));
    }
    public static void range(BigDecimal value,int precision,int scale){if(value.setScale(scale,RoundingMode.UNNECESSARY).precision()>precision)throw new ArithmeticException("수치 범위 초과");}
    private static String personName(Sources sources,String id) {
        return sources.people().stream().filter(p->p.personId().equals(id)).map(Person::name)
            .findFirst().orElse("인력 정보가 없는 사람");
    }
    private static String candidateProblem(String month,Candidate candidate,Sources sources,boolean range) {
        boolean cost=candidate.table().equals("TB_CO_MON_COST");
        boolean assignment=candidate.table().equals("TB_PJ_ASGN_MON");
        String subject=cost||assignment?personName(sources,s(candidate.row(),"dev_id"))+"의 ":"";
        String target=cost?"원가":assignment?"월별 투입":"손익";
        String screen=cost?"개인 원가·수익표":assignment?"인력 투입":"월별 손익";
        return subject+month+" "+target+(range?" 값이 저장할 수 있는 범위를 벗어나 마감할 수 없습니다. ":" 금액이 없어 마감할 수 없습니다. ")
            +screen+"에서 확인해 주세요.";
    }
    public CashImpact cashImpact(String month) {
        var impact=cashFlow.calculateCloseImpact(month);
        var before=impact.before();var after=impact.after();
        String taxBefore=amount(before,CashFlowDtos.Item.CORP_TAX),taxAfter=amount(after,CashFlowDtos.Item.CORP_TAX);
        return new CashImpact(subtract(amount(before,CashFlowDtos.Item.CASH_SALES),amount(after,CashFlowDtos.Item.CASH_SALES)),
            subtract(taxBefore,taxAfter),before.incomeTotal(),after.incomeTotal(),taxBefore,taxAfter,before.closingBalance(),after.closingBalance());
    }
    private static String amount(CashFlowDtos.Month month,CashFlowDtos.Item item) {
        return month.items().stream().filter(i->i.item()==item).findFirst().orElseThrow().amount();
    }
    public static Map<String,Object> row(Object... fields){Map<String,Object> map=new LinkedHashMap<>();for(int i=0;i<fields.length;i+=2)map.put(fields[i].toString(),fields[i+1]);return map;}
    public static String standard(Map<String,List<Map<String,Object>>> data,String code){return data.get("TB_SY_OPER_STD").stream().filter(r->code.equals(s(r,"std_cd"))).max(Comparator.comparing(r->s(r,"aply_bgn_ym"))).map(r->s(r,"std_val")).orElse(null);}
    private static BigDecimal decimal(String value){return value==null?null:new BigDecimal(value);}
    public static String api(String db){return db.substring(0,4)+"-"+db.substring(4);}
    private static String subtract(String a,String b){return BusinessPlanActuals.subtract(a,b);}
}
