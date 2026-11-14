package com.sicost.operating;

import com.sicost.businessplansops.BusinessPlanPerformanceDtos.Actual;
import com.sicost.structure.ProfitStructureDtos;
import com.sicost.structure.ProfitStructureDtos.Card;
import com.sicost.structure.ProfitStructureRepository;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.operating.OperatingProfitDtos.*;
import static com.sicost.structure.ProfitStructureService.*;

@Service
public class OperatingProfitService {
    private final ProfitStructureRepository repository;
    public OperatingProfitService(ProfitStructureRepository repository) {this.repository=repository;}
    public static final List<String> METRICS=List.of("salesAmt","salesCostAmt","allocCostAmt","idleCostAmt","drctExpsAmt","grossPrftAmt","sgaTotAmt","sgaInputAmt","indrctLaborAmt","rndAmt","prevAdjAmt","oprtPrftAmt","operatingProfitRate");
    private static final List<String> LABELS=List.of("매출액","매출원가","배분 원가","대기 원가","직접 경비","매출총이익","판관비","입력 항목","간접 인건비","연구개발비(내부)","전월 조정","영업이익","영업이익률");
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Data getOperatingProfitLoss(Query query) {
        var read=repository.read(new ProfitStructureDtos.Query(query.year(),query.month(),"YTD",query.amountUnit()));
        var keys=read.months().stream().map(Actual::month).filter(m->m.compareTo(query.month())<=0).toList();
        var previous=keys.stream().map(k->YearMonth.parse(k).minusYears(1).toString()).toList();
        var months=keys.stream().map(read.all()::get).toList();
        var total=aggregate(months);var completeness=coverage(months);
        boolean showPrevious=previous.stream().map(read.all()::get).filter(Objects::nonNull).anyMatch(m->m.completeness().availableMonthCount()>0);
        List<StatementRow> rows=new ArrayList<>();
        for(int i=0;i<METRICS.size();i++) {
            String metric=METRICS.get(i);boolean rate="operatingProfitRate".equals(metric);
            var comparison=showPrevious?compare(metric,keys,previous,read.all()):null;
            rows.add(new StatementRow(metric,LABELS.get(i),Set.of(2,3,4,7,8,9).contains(i)?1:0,rate,
                read.months().stream().map(m->m.amounts().get(metric)).toList(),total.get(metric),comparison==null?null:comparison.comparisonValue(),comparison));
        }
        List<Card> summary=new ArrayList<>();
        for(String metric:List.of("salesAmt","grossPrftAmt","oprtPrftAmt"))summary.add(new Card(metric,total.get(metric),metric.equals("oprtPrftAmt")?total.get("operatingProfitRate"):null,null,showPrevious?compare(metric,keys,previous,read.all()):null));
        Actual selected=read.all().get(query.month());
        summary.add(new Card("currentMonthOperatingProfit",selected.amounts().get("oprtPrftAmt"),selected.amounts().get("operatingProfitRate"),
            compare("oprtPrftAmt",List.of(query.month()),List.of(YearMonth.parse(query.month()).minusMonths(1).toString()),read.all()),null));
        List<String> notices=new ArrayList<>(read.notices());
        notices.add("자료 "+completeness.availableMonthCount()+"/"+keys.size()+"개월");
        if(!completeness.missingMonths().isEmpty())notices.add("빠진 월: "+String.join(", ",completeness.missingMonths()));
        if(showPrevious) {
            var comparison=rows.get(0).comparison();
            notices.add("전년 자료 "+comparison.availableMonthCount()+"/"+comparison.requestedMonthCount()+"개월"+(comparison.missingMonths().isEmpty()?"":" · 빠진 월: "+String.join(", ",comparison.missingMonths())));
        }
        return new Data(read.context(),read.months(),List.copyOf(summary),List.copyOf(rows),showPrevious,
            months.stream().anyMatch(Actual::provisional),completeness,notices.stream().distinct().toList());
    }
}
