package com.sicost.structure;

import com.sicost.businessplansops.BusinessPlanPerformanceDtos.*;
import com.sicost.codegroups.CodeGroupMapper;
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
        String[] names={"매출","매출원가","배분 원가","대기 원가","직접 경비","매출총이익","매출총이익률","판관비","전월 조정","영업이익","영업이익률","영업외수익","영업외비용","법인세(월할)","당기순이익","대기 원가 비중","배분 기준 매출(참고)"};
        for(int i=0;i<keys.length;i++)labels.put(keys[i],names[i]);LABELS=Collections.unmodifiableMap(labels);
    }
    private final ProfitStructureRepository repository;
    private final RevenueCompositionCalculation compositions;
    private final CodeGroupMapper codes;
    public ProfitStructureService(ProfitStructureRepository repository,
        RevenueCompositionCalculation compositions,CodeGroupMapper codes) {
        this.repository=repository;
        this.compositions=compositions;
        this.codes=codes;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Data getProfitLossStructure(Query query) {
        Read read=repository.read(query);List<List<String>> sets=bucketMonths(query);
        var entries=compositions.entries(read);List<String> top=compositions.topCustomers(entries,sets.get(sets.size()-1));
        Map<String,String> typeNames=new HashMap<>();
        if(!entries.isEmpty())codes.codes("CTRT_TYPE",false).forEach(code->typeNames.put(code.code(),code.name()));
        List<Bucket> buckets=new ArrayList<>();
        for(var keys:sets) {
            List<Actual> months=keys.stream().map(read.all()::get).toList();Map<String,String> amounts=aggregate(months);
            List<Comparison> comparisons=new ArrayList<>();
            for(String metric:LABELS.keySet())if(!"referenceRevenue".equals(metric)) {
                if(!"YTD".equals(query.aggregation()))comparisons.add(compare(metric,keys,previous(keys,query.aggregation()),read.all()));
                comparisons.add(compare(metric,keys,keys.stream().map(k->YearMonth.parse(k).minusYears(1).toString()).toList(),read.all()));
            }
            String through=keys.get(keys.size()-1),key=switch(query.aggregation()){case "QUARTER"->query.year()+"-Q"+((YearMonth.parse(through).getMonthValue()-1)/3+1);case "YTD"->through+" 누계";default->through;};
            Composition composition=displayComposition(
                compositions.composition(entries,months,amounts,top),entries,keys,top,typeNames);
            buckets.add(new Bucket(key,keys,through,keys.size(),amounts,coverage(months),months.stream().anyMatch(Actual::provisional),List.copyOf(comparisons),composition,compositions.reference(read,months)));
        }
        Bucket last=buckets.get(buckets.size()-1);List<Card> summary=new ArrayList<>();
        for(String metric:List.of("salesAmt","grossPrftAmt","oprtPrftAmt","idleCostAmt")) {
            String rate=switch(metric){case "grossPrftAmt"->"grossProfitRate";case "oprtPrftAmt"->"operatingProfitRate";case "idleCostAmt"->"idleCostShare";default->null;};
            String comparisonMetric="idleCostAmt".equals(metric)?"idleCostShare":metric;
            var matches=last.comparisons().stream().filter(c->comparisonMetric.equals(c.metric())).toList();
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
        List<Waterfall> waterfall=waterfall(last.amounts());
        if(!last.composition().visible()&&waterfall.stream().anyMatch(w->"CHANGE".equals(w.kind())&&!w.connected()))
            notices.add("이관 세부금액 없음/구성 불일치: 확인되지 않는 폭포 구간은 연결하지 않습니다.");
        return new Data(read.context(),read.months(),List.copyOf(buckets),List.copyOf(summary),waterfall,List.copyOf(numeric),methods(),notices.stream().distinct().toList());
    }
    // UI-07 names only: the shared composition calculation also serves the net profit screen.
    private static Composition displayComposition(Composition source,
        List<RevenueCompositionCalculation.Entry> entries,List<String> months,
        List<String> top,Map<String,String> typeNames) {
        if(!source.visible())return source;
        long otherCount=entries.stream().filter(entry->months.contains(entry.month()))
            .map(entry->Objects.toString(entry.customerId(),"UNASSIGNED"))
            .filter(id->!top.contains(id)).distinct().count();
        List<Group> types=source.contractTypes().stream()
            .map(group->named(group,typeNames.getOrDefault(group.groupId(),group.groupName()))).toList();
        List<Group> customers=source.customers().stream()
            .map(group->group.other()?named(group,"그 밖 "+otherCount+"곳"):group).toList();
        return new Composition(source.visible(),source.reason(),source.complete(),source.revenueDifference(),
            types,customers,source.salesParts());
    }
    private static Group named(Group group,String name) {
        return new Group(group.groupKind(),group.groupId(),name,group.other(),group.salesAmt(),
            group.allocatedLaborCostAmt(),group.contributionAmt(),group.revenueShare(),
            group.contributionRate(),group.positiveContributionShare(),group.loss(),group.showBar());
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
                BigDecimal numerator=an.multiply(bd).subtract(bn.multiply(ad)).multiply(new BigDecimal("100")),denominator=ad.multiply(bd);
                points=numerator.divide(denominator,1,RoundingMode.HALF_UP).toPlainString();
                delta=new BigDecimal(points);
            } else if(new BigDecimal(before).signum()!=0)change=delta.multiply(new BigDecimal("100")).divide(new BigDecimal(before).abs(),1,RoundingMode.HALF_UP).toPlainString();
            boolean cost=Set.of("salesCostAmt","allocCostAmt","idleCostAmt","drctExpsAmt","sgaInputAmt","indrctLaborAmt","rndAmt","sgaTotAmt","noprExpsAmt","corpTaxAmt","idleCostShare").contains(metric);
            direction=delta.signum()==0?"NEUTRAL":(delta.signum()>0)==cost?"ADVERSE":"FAVORABLE";
        }
        return new Comparison(metric,keys,previous,value,before,change,points,paired,previous.size()-missing.size(),previous.size(),missing,direction);
    }
    static List<Waterfall> waterfall(Map<String,String> a) {
        List<Waterfall> result=new ArrayList<>();String[] metrics={"salesAmt","salesCostAmt","grossPrftAmt","sgaTotAmt","prevAdjAmt","oprtPrftAmt","nonOperatingNet","corpTaxAmt","netPrftAmt"};
        boolean gross=reconciles(a.get("grossPrftAmt"),subtract(a.get("salesAmt"),a.get("salesCostAmt")));
        boolean operating=reconciles(a.get("oprtPrftAmt"),add(subtract(a.get("grossPrftAmt"),a.get("sgaTotAmt")),a.get("prevAdjAmt")));
        boolean net=reconciles(a.get("netPrftAmt"),subtract(add(a.get("oprtPrftAmt"),a.get("noprIncmAmt")),add(a.get("noprExpsAmt"),a.get("corpTaxAmt"))));
        boolean known=true;for(String metric:metrics) {
            boolean subtotal=Set.of("salesAmt","grossPrftAmt","oprtPrftAmt","netPrftAmt").contains(metric);
            String value=metric.equals("nonOperatingNet")?subtract(a.get("noprIncmAmt"),a.get("noprExpsAmt")):a.get(metric);
            if(Set.of("salesCostAmt","sgaTotAmt","corpTaxAmt").contains(metric)&&value!=null)value=new BigDecimal(value).negate().toPlainString();
            boolean reconciled=switch(metric) {
                case "salesCostAmt"->gross;
                case "sgaTotAmt","prevAdjAmt"->operating;
                case "nonOperatingNet","corpTaxAmt"->net;
                default->true;
            };
            boolean connected=known&&value!=null&&reconciled;if(subtotal)known=value!=null;else known=connected;
            result.add(new Waterfall(metric,metric.equals("nonOperatingNet")?"영업외손익":LABELS.get(metric),subtotal?"SUBTOTAL":"CHANGE",value,connected));
        }return List.copyOf(result);
    }
    private static boolean reconciles(String stored,String calculated) {
        return stored!=null&&calculated!=null&&new BigDecimal(stored).compareTo(new BigDecimal(calculated))==0;
    }
    static List<Method> methods() {
        return List.of(
            new Method("기간",
                List.of("요약 카드와 폭포 차트는 맨 오른쪽(가장 최근) 기간 기준"),
                "월: 그 달 · 분기: 그 분기 안의 달을 더함(진행 중인 분기는 기준월까지) · 연 누계: 1월부터 그 달까지 더함",
                false,true),
            new Method("매출총이익(률)",
                List.of("매출원가 = 배분 원가(내부 제외) + 대기 원가 + 직접 경비"),
                "매출 − 매출원가 · 매출총이익 ÷ 매출 × 100",false,true),
            new Method("영업이익(률)",
                List.of("판관비 = 재무 항목 입력 + 간접 인건비 + 연구개발비"),
                "매출총이익 − 판관비 ± 전월 조정 · 영업이익 ÷ 매출 × 100",false,true),
            new Method("당기순이익",
                List.of("법인세(월할)는 재무 항목 화면의 연간 예상액에서 계산"),
                "영업이익 + 영업외수익 − 영업외비용 − 법인세(월할)",false,true),
            new Method("대기 원가 비중",
                List.of("투입되지 않은 정규·계약직 인건비가 원가에서 차지하는 몫"),
                "대기 원가 ÷ 매출원가 × 100",false,true),
            new Method("배분 기준 매출(참고선)",
                List.of("턴키는 청구한 달에만 매출이 잡혀 출렁이므로, 일한 만큼 나눈 금액으로 바꿔 본 선. 회사 매출에 더하지 않음"),
                "월단위·파견 매출 + 턴키 배분표 금액(배분표가 없으면 그 달 원가 × 계약금액 ÷ 예산)",true,true),
            new Method("매출 구성",
                List.of("영업 파트는 프로젝트를 따온 파트 기준 · 대기 원가·판관비는 나누지 않음 · 회사 이익과 다름"),
                "계약 유형·고객사·영업 파트별로 매출과 배분 원가를 더함 · 이익률 = (매출 − 배분 원가) ÷ 매출",false,true));
    }
}
