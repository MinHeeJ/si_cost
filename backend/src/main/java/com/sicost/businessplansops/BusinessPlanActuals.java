package com.sicost.businessplansops;

import com.sicost.profitability.MonthlyCostDtos.*;
import com.sicost.profitability.MonthlyCostService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import static com.sicost.businessplansops.BusinessPlanPerformanceDtos.*;

@Component
public class BusinessPlanActuals {
    public static final List<String> AMOUNTS=List.of("salesAmt","allocCostAmt","idleCostAmt","drctExpsAmt","salesCostAmt","grossPrftAmt","sgaInputAmt","indrctLaborAmt","rndAmt","sgaTotAmt","prevAdjAmt","oprtPrftAmt","noprIncmAmt","noprExpsAmt","corpTaxAmt","netPrftAmt");
    public Actual absent(String month,String kind,String status,OffsetDateTime at) {
        Map<String,String> amounts=empty();
        boolean future="FUTURE".equals(kind);
        String reason="CLOSE".equals(status)?"마감 손익 자료 미확인":"이관 손익 자료 없음";
        return new Actual(month,kind,status,false,false,false,amounts,
            new Completeness(false,false,false,future?List.of():AMOUNTS,List.of(),List.of(),
                future?List.of():List.of(new Issue(month,"salesAmt","CLOSE".equals(status)?"CLOSED_SNAPSHOT_MISSING":"MIGRATED_MONTH_MISSING",reason,false)),0,1,future?List.of():List.of(month)),List.of(),at);
    }
    public boolean validClosed(Snapshot s) {
        return s!=null&&"Y".equals(s.fixed())&&"N".equals(s.migrated())&&s.salesAmt()!=null
            &&s.salesCostAmt()!=null&&s.sgaTotAmt()!=null&&s.oprtPrftAmt()!=null;
    }
    public Actual stored(Snapshot s,boolean migrated,String status,OffsetDateTime at) {
        BigDecimal[] values={s.salesAmt(),s.allocCostAmt(),s.idleCostAmt(),s.drctExpsAmt(),s.salesCostAmt(),s.grossPrftAmt(),s.sgaInputAmt(),s.indrctLaborAmt(),s.rndAmt(),s.sgaTotAmt(),s.prevAdjAmt(),s.oprtPrftAmt(),s.noprIncmAmt(),s.noprExpsAmt(),s.corpTaxAmt(),s.netPrftAmt()};
        Map<String,String> amounts=new LinkedHashMap<>();List<Source> sources=new ArrayList<>();String month=api(s.month());
        for(int i=0;i<AMOUNTS.size();i++) {amounts.put(AMOUNTS.get(i),money(values[i]));sources.add(new Source(month,"TB_CO_MON_PL",s.month(),AMOUNTS.get(i),money(values[i]),false,"저장 사본 원값"));}
        rates(amounts);
        return new Actual(month,migrated?"MIGRATED":"CLOSED_SNAPSHOT",status,"Y".equals(s.fixed()),migrated,false,amounts,
            complete(month,amounts,List.of(),List.of(),true),List.copyOf(sources),at);
    }
    public record OperatingSources(List<MonthlyPersonCost> people,List<Source> financial,Sources originals) {}
    public Actual calculated(String month,OperatingSources input,String tax,OffsetDateTime at) {
        String key=month.replace("-","");
        var persons=input.people().stream().filter(p->key.equals(p.month())).toList();
        var rows=persons.stream().flatMap(p->p.rows().stream()).toList();
        List<Source> sources=new ArrayList<>(input.financial().stream().filter(s->month.equals(s.month())).toList());
        List<Issue> issues=new ArrayList<>();List<String> estimated=new ArrayList<>();
        for(var p:persons) {
            if(p.completeness().estimated())estimated.add("급여 예정: "+p.personId());
            p.completeness().issues().forEach(i->issues.add(new Issue(month,i.source(),"SOURCE_UNCONFIRMED",i.message(),p.completeness().estimated())));
        }
        Map<String,String> values=empty();
        // An unknown salary in an identified class only invalidates that class's sums.
        // Missing allocation coverage still makes the company costs unconfirmed.
        boolean unallocated=persons.stream().anyMatch(p->!p.completeness().allocationComplete()
            &&(p.rows().isEmpty()||input.originals().protectedPersonMonth(key,p.personId())))
            ||rows.stream().anyMatch(r->!Set.of("ALLOC","PRE","IDLE","INDRCT","RND").contains(Objects.toString(r.costClass(),"")));
        values.put("allocCostAmt",unallocated?null:sum(rows.stream().filter(r->("ALLOC".equals(r.costClass())||"PRE".equals(r.costClass()))&&!MonthlyCostService.synthetic(r)).toList(),MonthlyCostRow::laborAllocated));
        values.put("idleCostAmt",unallocated?null:sum(rows.stream().filter(r->"IDLE".equals(r.costClass())).toList(),MonthlyCostRow::laborAllocated));
        values.put("indrctLaborAmt",unallocated?null:sum(rows.stream().filter(r->"INDRCT".equals(r.costClass())).toList(),MonthlyCostRow::laborAllocated));
        String direct=sum(rows.stream().filter(r->"ALLOC".equals(r.costClass())||"PRE".equals(r.costClass())||"IDLE".equals(r.costClass())).toList(),MonthlyCostRow::directExpense);
        values.put("drctExpsAmt",unallocated?null:add(direct,sourceSum(sources,"directExtra")));
        String rnd=sum(rows.stream().filter(r->"RND".equals(r.costClass())).toList(),r->add(r.laborAllocated(),r.directExpense()));
        values.put("rndAmt",unallocated?null:add(rnd,sourceSum(sources,"rndExtra")));
        var paid=rows.stream().filter(r->("MO".equals(r.contractType())||"DP".equals(r.contractType()))&&"ALLOC".equals(r.costClass())&&!"FREE".equals(r.inputType())&&!MonthlyCostService.synthetic(r)).toList();
        String revenue=sum(paid,MonthlyCostRow::personalRevenue);
        boolean missingAssignment=input.originals().assignments().stream().filter(a->key.equals(a.month())&&!"CNCL".equals(a.status())&&a.assignmentMm()!=null&&a.assignmentMm().signum()>0&&!"FREE".equals(a.inputType()))
            .filter(a->input.originals().projects().stream().anyMatch(p->p.projectId().equals(a.projectId())&&("MO".equals(p.contractType())||"DP".equals(p.contractType()))&&!"PRE".equals(p.status())&&!"LOSE".equals(p.status())))
            .anyMatch(a->paid.stream().noneMatch(r->r.key().equals(new CostKey(a.month(),a.personId(),a.projectId(),a.assignmentId()))));
        if(missingAssignment)revenue=null;
        values.put("salesAmt",add(revenue,sourceSum(sources,"tkSales")));
        for(String metric:List.of("sgaInputAmt","noprIncmAmt","noprExpsAmt","prevAdjAmt"))values.put(metric,sourceSum(sources,metric));
        values.put("salesCostAmt",add(values.get("allocCostAmt"),values.get("idleCostAmt"),values.get("drctExpsAmt")));
        values.put("grossPrftAmt",subtract(values.get("salesAmt"),values.get("salesCostAmt")));
        values.put("sgaTotAmt",add(values.get("sgaInputAmt"),values.get("indrctLaborAmt"),values.get("rndAmt")));
        values.put("oprtPrftAmt",add(subtract(values.get("grossPrftAmt"),values.get("sgaTotAmt")),values.get("prevAdjAmt")));
        values.put("corpTaxAmt",tax);
        values.put("netPrftAmt",subtract(add(values.get("oprtPrftAmt"),values.get("noprIncmAmt")),add(values.get("noprExpsAmt"),tax)));
        for(var r:rows) {
            String identity=key+":"+r.personId()+":"+r.projectId()+":"+r.assignmentId();
            sources.add(new Source(month,"TB_CO_MON_COST",identity,"laborAllocated",r.laborAllocated(),r.estimated(),"전체 월 원가 공통 계산·간접/휴직 포함"));
            sources.add(new Source(month,"TB_CO_MON_COST",identity,"directExpense",r.directExpense(),r.estimated(),"팀비·프로젝트경비 포함; 중복 가산 없음"));
            if(paid.contains(r))sources.add(new Source(month,"TB_PJ_ASGN_MON",identity,"salesAmt",r.personalRevenue(),r.estimated(),"MO/DP 유상 귀속월 매출; MM 재곱 없음"));
        }
        sources.add(new Source(month,"TB_FI_CORP_TAX",month.substring(0,4),"corpTaxAmt",tax,false,"공통 FinancialCalculation.allocate; 미래 운영월 포함"));
        rates(values);
        return new Actual(month,"CALCULATED","WORK",false,false,true,values,
            complete(month,values,issues,estimated,true),List.copyOf(sources),at);
    }
    private Completeness complete(String month,Map<String,String> amounts,List<Issue> supplied,List<String> estimated,boolean available) {
        List<String> missing=AMOUNTS.stream().filter(k->amounts.get(k)==null).toList();List<Issue> issues=new ArrayList<>(supplied);
        missing.forEach(m->issues.add(new Issue(month,m,"AMOUNT_UNCONFIRMED","해당 지표 원천 미확인; 0으로 보정하지 않습니다.",false)));
        boolean cost=List.of("allocCostAmt","idleCostAmt","drctExpsAmt","indrctLaborAmt","rndAmt").stream().allMatch(k->amounts.get(k)!=null);
        return new Completeness(cost,amounts.get("salesAmt")!=null,amounts.get("corpTaxAmt")!=null,missing,List.copyOf(estimated),available?List.of(month):List.of(),List.copyOf(issues),available?1:0,1,available?List.of():List.of(month));
    }
    public static Map<String,String> empty() {Map<String,String> values=new LinkedHashMap<>();AMOUNTS.forEach(k->values.put(k,null));rates(values);return values;}
    private static void rates(Map<String,String> v) {
        v.put("grossProfitRate",ratio(v.get("grossPrftAmt"),v.get("salesAmt"),false));
        v.put("operatingProfitRate",ratio(v.get("oprtPrftAmt"),v.get("salesAmt"),false));
        v.put("netProfitRate",ratio(v.get("netPrftAmt"),v.get("salesAmt"),false));
        v.put("idleCostShare",ratio(v.get("idleCostAmt"),v.get("salesCostAmt"),false));
    }
    private static String sourceSum(List<Source> rows,String metric) {return sum(rows.stream().filter(r->metric.equals(r.metric())).toList(),Source::amount);}
    public static <T> String sum(List<T> rows,Function<T,String> field) {BigDecimal total=BigDecimal.ZERO;for(var row:rows){String value=field.apply(row);if(value==null)return null;total=total.add(new BigDecimal(value));}return money(total);}
    public static String add(String... values) {BigDecimal total=BigDecimal.ZERO;for(String v:values){if(v==null)return null;total=total.add(new BigDecimal(v));}return money(total);}
    public static String subtract(String a,String b) {return a==null||b==null?null:money(new BigDecimal(a).subtract(new BigDecimal(b)));}
    public static String ratio(String a,String b,boolean positiveOnly) {return a==null||b==null||new BigDecimal(b).signum()==0||(positiveOnly&&new BigDecimal(b).signum()<0)?null:new BigDecimal(a).multiply(new BigDecimal("100")).divide(new BigDecimal(b),1,RoundingMode.HALF_UP).toPlainString();}
    public static String money(BigDecimal n) {return n==null?null:n.setScale(0,RoundingMode.DOWN).toPlainString();}
    public static String api(String value) {return value.substring(0,4)+"-"+value.substring(4);}
}
