package com.sicost.structure;

import com.sicost.businessplansops.BusinessPlanPerformanceDtos.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.businessplansops.BusinessPlanActuals.*;
import static com.sicost.structure.ProfitStructureDtos.*;
import static com.sicost.structure.ProfitStructureRepository.*;
import com.sicost.structure.ProfitStructureDtos.Query;
import com.sicost.structure.ProfitStructureDtos.Data;

@Service
public class ProfitStructureService {
    public static final Map<String,String> LABELS;
    static {
        Map<String,String> labels=new LinkedHashMap<>();
        String[] keys={"salesAmt","salesCostAmt","allocCostAmt","idleCostAmt","drctExpsAmt","grossPrftAmt","grossProfitRate","sgaTotAmt","prevAdjAmt","oprtPrftAmt","operatingProfitRate","noprIncmAmt","noprExpsAmt","corpTaxAmt","netPrftAmt","idleCostShare","referenceRevenue"};
        String[] names={"매출","매출원가","배분 원가","대기 원가","직접 경비","매출총이익","총이익률","판관비","전월 조정","영업이익","영업이익률","영업외수익","영업외비용","월할 세금","순이익","대기 비중","배분 기준 매출(참고)"};
        for(int i=0;i<keys.length;i++)labels.put(keys[i],names[i]);LABELS=Collections.unmodifiableMap(labels);
    }
    private final ProfitStructureRepository repository;
    private final RevenueCompositionCalculation compositions;
    public ProfitStructureService(ProfitStructureRepository repository,RevenueCompositionCalculation compositions) {this.repository=repository;this.compositions=compositions;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Data getProfitLossStructure(Query query) {
        Read read=repository.read(query);List<List<String>> sets=bucketMonths(query);
        var entries=compositions.entries(read);List<String> top=compositions.topCustomers(entries,sets.get(sets.size()-1));
        List<Bucket> buckets=new ArrayList<>();
        for(var keys:sets) {
            List<Actual> months=keys.stream().map(read.all()::get).toList();Map<String,String> amounts=aggregate(months);
            List<Comparison> comparisons=new ArrayList<>();
            for(String metric:LABELS.keySet())if(!"referenceRevenue".equals(metric)) {
                if(!"YTD".equals(query.aggregation()))comparisons.add(compare(metric,keys,previous(keys,query.aggregation()),read.all()));
                comparisons.add(compare(metric,keys,keys.stream().map(k->YearMonth.parse(k).minusYears(1).toString()).toList(),read.all()));
            }
            String through=keys.get(keys.size()-1),key=switch(query.aggregation()){case "QUARTER"->query.year()+"-Q"+((YearMonth.parse(through).getMonthValue()-1)/3+1);case "YTD"->through+" 누계";default->through;};
            buckets.add(new Bucket(key,keys,through,keys.size(),amounts,coverage(months),months.stream().anyMatch(Actual::provisional),List.copyOf(comparisons),compositions.composition(entries,months,amounts,top),compositions.reference(read,months)));
        }
        Bucket last=buckets.get(buckets.size()-1);List<Card> summary=new ArrayList<>();
        for(String metric:List.of("salesAmt","grossPrftAmt","oprtPrftAmt","idleCostAmt")) {
            String rate=switch(metric){case "grossPrftAmt"->"grossProfitRate";case "oprtPrftAmt"->"operatingProfitRate";case "idleCostAmt"->"idleCostShare";default->null;};
            var matches=last.comparisons().stream().filter(c->metric.equals(c.metric())).toList();
            Comparison prev="YTD".equals(query.aggregation())?null:matches.get(0),year=matches.get(matches.size()-1);
            if(year.availableMonthCount()==0)year=null;
            summary.add(new Card(metric,last.amounts().get(metric),rate==null?null:last.amounts().get(rate),prev,year));
        }
        List<NumericRow> numeric=new ArrayList<>();
        LABELS.forEach((metric,label)->numeric.add(new NumericRow(metric,label,isRate(metric),buckets.stream().map(Bucket::key).toList(),buckets.stream().map(b->"referenceRevenue".equals(metric)?b.referenceRevenue().amount():b.amounts().get(metric)).toList())));
        List<String> notices=new ArrayList<>(read.notices());
        for(Bucket b:buckets) {
            if(b.completeness().availableMonthCount()!=b.monthCount())notices.add(b.key()+" 자료 "+b.completeness().availableMonthCount()+"/"+b.monthCount()+"개월 · 빠진 월: "+String.join(", ",b.completeness().missingMonths()));
            if(!b.composition().visible())notices.add(RevenueCompositionCalculation.MIGRATED);
        }
        return new Data(read.context(),read.months(),List.copyOf(buckets),List.copyOf(summary),waterfall(last.amounts()),List.copyOf(numeric),methods(),notices.stream().distinct().toList());
    }
    static List<List<String>> bucketMonths(Query query) {
        List<List<String>> result=new ArrayList<>();int end=YearMonth.parse(query.month()).getMonthValue();
        for(int m=1;m<=end;m++) {
            if("QUARTER".equals(query.aggregation())&&m%3!=0&&m!=end)continue;
            int start="MONTH".equals(query.aggregation())?m:"QUARTER".equals(query.aggregation())?((m-1)/3)*3+1:1;
            List<String> months=new ArrayList<>();for(int n=start;n<=m;n++)months.add(YearMonth.of(query.year(),n).toString());result.add(List.copyOf(months));
        }
        return List.copyOf(result);
    }
    static List<String> previous(List<String> keys,String aggregation) {
        if("MONTH".equals(aggregation))return List.of(YearMonth.parse(keys.get(0)).minusMonths(1).toString());
        YearMonth first=YearMonth.parse(keys.get(0)).minusMonths(3);List<String> previous=new ArrayList<>();for(int i=0;i<keys.size();i++)previous.add(first.plusMonths(i).toString());return previous;
    }
    static boolean isRate(String metric) {return metric.endsWith("Rate")||metric.equals("idleCostShare");}
    public static Map<String,String> aggregate(List<Actual> months) {
        var present=months.stream().filter(Objects::nonNull).filter(m->m.completeness().issues().stream().noneMatch(i->"MIGRATED_MONTH_MISSING".equals(i.code()))).toList();
        Map<String,String> values=new LinkedHashMap<>();
        for(String metric:AMOUNTS)values.put(metric,present.isEmpty()?null:sum(present,m->m.amounts().get(metric)));
        values.put("grossProfitRate",ratio(values.get("grossPrftAmt"),values.get("salesAmt"),false));values.put("operatingProfitRate",ratio(values.get("oprtPrftAmt"),values.get("salesAmt"),false));values.put("netProfitRate",ratio(values.get("netPrftAmt"),values.get("salesAmt"),false));values.put("idleCostShare",ratio(values.get("idleCostAmt"),values.get("salesCostAmt"),false));return values;
    }
    public static Completeness coverage(List<Actual> months) {
        var present=months.stream().filter(Objects::nonNull).toList();var available=present.stream().filter(m->m.completeness().availableMonthCount()>0).toList();
        List<String> missing=present.stream().flatMap(m->m.completeness().missing().stream()).distinct().toList();
        return new Completeness(present.stream().allMatch(m->m.completeness().costComplete()),present.stream().allMatch(m->m.completeness().revenueComplete()),present.stream().allMatch(m->m.completeness().taxComplete()),missing,present.stream().flatMap(m->m.completeness().estimated().stream()).distinct().toList(),available.stream().map(Actual::month).toList(),present.stream().flatMap(m->m.completeness().issues().stream()).toList(),available.size(),months.size(),present.stream().filter(m->m.completeness().availableMonthCount()==0).map(Actual::month).toList());
    }
    public static Comparison compare(String metric,List<String> keys,List<String> previous,Map<String,Actual> all) {
        List<Actual> current=keys.stream().map(all::get).toList(),past=previous.stream().map(all::get).toList();
        String value=aggregate(current).get(metric),before=aggregate(past).get(metric);
        List<String> missing=previous.stream().filter(k->all.get(k)==null||all.get(k).completeness().availableMonthCount()==0).toList();
        boolean paired=missing.isEmpty()&&current.stream().allMatch(m->m!=null&&m.completeness().availableMonthCount()>0)&&value!=null&&before!=null;
        String change=null,points=null,direction="UNKNOWN";
        if(paired) {
            BigDecimal delta=new BigDecimal(value).subtract(new BigDecimal(before));
            if(isRate(metric)) {
                String[] parts=metric.equals("idleCostShare")?new String[]{"idleCostAmt","salesCostAmt"}:new String[]{metric.equals("grossProfitRate")?"grossPrftAmt":metric.equals("operatingProfitRate")?"oprtPrftAmt":"netPrftAmt","salesAmt"};
                var a=aggregate(current);var b=aggregate(past);
                BigDecimal an=new BigDecimal(a.get(parts[0])),ad=new BigDecimal(a.get(parts[1])),bn=new BigDecimal(b.get(parts[0])),bd=new BigDecimal(b.get(parts[1]));
                delta=an.multiply(bd).subtract(bn.multiply(ad)).multiply(new BigDecimal("100")).divide(ad.multiply(bd),12,RoundingMode.HALF_UP);
                points=delta.setScale(1,RoundingMode.HALF_UP).toPlainString();
            } else if(new BigDecimal(before).signum()!=0)change=delta.multiply(new BigDecimal("100")).divide(new BigDecimal(before).abs(),1,RoundingMode.HALF_UP).toPlainString();
            boolean cost=Set.of("salesCostAmt","allocCostAmt","idleCostAmt","drctExpsAmt","sgaInputAmt","indrctLaborAmt","rndAmt","sgaTotAmt","noprExpsAmt","corpTaxAmt","idleCostShare").contains(metric);
            direction=delta.signum()==0?"NEUTRAL":(delta.signum()>0)==cost?"ADVERSE":"FAVORABLE";
        }
        return new Comparison(metric,keys,previous,value,before,change,points,paired,previous.size()-missing.size(),previous.size(),missing,direction);
    }
    static List<Waterfall> waterfall(Map<String,String> a) {
        List<Waterfall> result=new ArrayList<>();String[] metrics={"salesAmt","salesCostAmt","grossPrftAmt","sgaTotAmt","prevAdjAmt","oprtPrftAmt","nonOperatingNet","corpTaxAmt","netPrftAmt"};
        boolean known=true;for(String metric:metrics) {
            boolean subtotal=Set.of("salesAmt","grossPrftAmt","oprtPrftAmt","netPrftAmt").contains(metric);
            String value=metric.equals("nonOperatingNet")?subtract(a.get("noprIncmAmt"),a.get("noprExpsAmt")):a.get(metric);
            if(Set.of("salesCostAmt","sgaTotAmt","corpTaxAmt").contains(metric)&&value!=null)value=new BigDecimal(value).negate().toPlainString();
            boolean connected=known&&value!=null;if(subtotal)known=value!=null;else known=connected;
            result.add(new Waterfall(metric,metric.equals("nonOperatingNet")?"영업외손익":LABELS.get(metric),subtotal?"SUBTOTAL":"CHANGE",value,connected));
        }return List.copyOf(result);
    }
    static List<Method> methods() {
        return List.of(new Method("salesAmt",List.of("TB_PJ_ASGN_MON","TB_BL_BILL"),"MO/DP 유상 귀속월 SALES_AMT + TK BILL_DT월 유효 SPLY_AMT; MM 재곱 없음; 입금액 아님",false,true),
            new Method("salesCostAmt",List.of("TB_CO_MON_COST","TB_PJ_DRCT_EXPS"),"배분 노동비 + 대기 노동비 + 직접 경비(팀비·프로젝트경비 포함) + 비IN 추가경비",false,true),
            new Method("oprtPrftAmt",List.of("TB_FI_ITEM","TB_CO_PREV_ADJ","TB_CO_MON_COST"),"매출총이익 − (판관비 입력 + 간접 인건비 + 연구개발비) + 전월 조정",false,true),
            new Method("netPrftAmt",List.of("TB_FI_ITEM","TB_FI_CORP_TAX","TB_CO_MON_PL"),"영업이익 + 영업외수익 − 영업외비용 − 공통 법인세 월할; 마감/이관 사본은 원값",false,true),
            new Method("rates",List.of("TB_CO_MON_PL","월 계산 결과"),"기간 이익 합 ÷ 기간 매출 합 × 100; 대기 비중 = 기간 대기 원가 합 ÷ 매출원가 합; 분모0은 미확인",false,true),
            new Method("referenceRevenue",List.of("TB_PJ_TK_DSTRB","TB_PJ_PROJ","전체 월 원가"),"MO/DP + TK PLAN>CTRT>EST 월 DEV+ITEM 합; 표 없을 때만 DOWN(전체 월 원가×현재 계약액/저장 예산); 누적 진행률 매출과 다를 수 있음",true,true),
            new Method("composition",List.of("TB_BL_BILL","TB_CO_MON_COST","TB_PJ_PROJ_PART","TB_BS_CLNT"),"프로젝트 실제 매출 R·ALLOC/PRE 노동비 L을 유형/고객사/영업파트로 합산; 기여이익=R−L, 회사 이익과 다름",false,true));
    }
}
