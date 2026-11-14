package com.sicost.net;

import com.sicost.businessplansops.BusinessPlanPerformanceDtos.Actual;
import com.sicost.common.api.ApiException;
import com.sicost.operating.OperatingProfitDtos.Query;
import com.sicost.operating.OperatingProfitDtos.StatementRow;
import com.sicost.operating.OperatingProfitService;
import com.sicost.structure.*;
import com.sicost.structure.ProfitStructureDtos.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.businessplansops.BusinessPlanActuals.ratio;
import static com.sicost.structure.ProfitStructureService.*;

@Service
public class NetProfitService {
    private final ProfitStructureRepository repository;
    private final RevenueCompositionCalculation compositions;
    public NetProfitService(ProfitStructureRepository repository,RevenueCompositionCalculation compositions) {
        this.repository=repository;this.compositions=compositions;
    }
    public static final List<String> METRICS;
    private static final List<String> LABELS=List.of("매출액","매출원가","배분 원가","대기 원가","직접 경비","매출총이익","판관비","입력 항목","간접 인건비","연구개발비(내부)","전월 조정","영업이익","영업이익률","영업외수익","영업외비용","법인세(월할)","당기순이익","순이익률");
    static {
        List<String> metrics=new ArrayList<>(OperatingProfitService.METRICS);
        metrics.addAll(List.of("noprIncmAmt","noprExpsAmt","corpTaxAmt","netPrftAmt","netProfitRate"));
        METRICS=List.copyOf(metrics);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public NetProfitDtos.Data getNetProfitLoss(Query query) {
        ProfitStructureRepository.Read read;
        try {read=repository.read(new ProfitStructureDtos.Query(query.year(),query.month(),"YTD",query.amountUnit()));}
        catch(ApiException failure) {
            if("INVALID_PERIOD".equals(failure.code()))throw ProfitStructureInput.invalid("month");
            throw failure;
        }
        var keys=read.months().stream().map(Actual::month).filter(m->m.compareTo(query.month())<=0).toList();
        var previous=keys.stream().map(k->YearMonth.parse(k).minusYears(1).toString()).toList();
        var months=keys.stream().map(read.all()::get).toList();
        var total=aggregate(months);var completeness=coverage(months);
        boolean showPrevious=previous.stream().map(read.all()::get).filter(Objects::nonNull).anyMatch(m->m.completeness().availableMonthCount()>0);
        List<StatementRow> rows=new ArrayList<>();
        for(int i=0;i<METRICS.size();i++) {
            String metric=METRICS.get(i);var comparison=showPrevious?compare(metric,keys,previous,read.all()):null;
            rows.add(new StatementRow(metric,LABELS.get(i),Set.of(2,3,4,7,8,9).contains(i)?1:0,metric.endsWith("Rate"),
                read.months().stream().map(m->m.amounts().get(metric)).toList(),total.get(metric),comparison==null?null:comparison.comparisonValue(),comparison));
        }
        Actual selected=read.all().get(query.month());
        List<Card> summary=List.of(new Card("netPrftAmt",total.get("netPrftAmt"),total.get("netProfitRate"),null,
            showPrevious?compare("netPrftAmt",keys,previous,read.all()):null),
            new Card("currentMonthNetProfit",selected.amounts().get("netPrftAmt"),selected.amounts().get("netProfitRate"),
                compare("netPrftAmt",List.of(query.month()),List.of(YearMonth.parse(query.month()).minusMonths(1).toString()),read.all()),null));
        var entries=compositions.entries(read);
        var composition=compositions.composition(entries,List.of(selected),selected.amounts(),compositions.topCustomers(entries,List.of(query.month())));
        composition=contribution(composition);
        List<String> notices=new ArrayList<>(read.notices());
        notices.add("자료 "+completeness.availableMonthCount()+"/"+keys.size()+"개월");
        if(!completeness.missingMonths().isEmpty())notices.add("빠진 월: "+String.join(", ",completeness.missingMonths()));
        if(showPrevious) {
            var comparison=rows.get(0).comparison();
            notices.add("전년 자료 "+comparison.availableMonthCount()+"/"+comparison.requestedMonthCount()+"개월"+(comparison.missingMonths().isEmpty()?"":" · 빠진 월: "+String.join(", ",comparison.missingMonths())));
        }
        if(composition.visible()&&composition.contractTypes().stream().noneMatch(g->g.showBar()))notices.add("기준월 양수 기여이익이 없어 기여도 비율을 표시하지 않습니다.");
        return new NetProfitDtos.Data(read.context(),read.months(),summary,List.copyOf(rows),showPrevious,
            months.stream().anyMatch(Actual::provisional),completeness,notices.stream().distinct().toList(),query.month(),composition);
    }
    static Composition contribution(Composition source) {
        if(!source.visible())return source;
        boolean known=source.contractTypes().stream().allMatch(g->g.contributionAmt()!=null);
        BigDecimal positive=source.contractTypes().stream().map(Group::contributionAmt).filter(Objects::nonNull)
            .map(BigDecimal::new).filter(n->n.signum()>0).reduce(BigDecimal.ZERO,BigDecimal::add);
        List<Group> groups=source.contractTypes().stream().map(g->{
            boolean gain=g.contributionAmt()!=null&&new BigDecimal(g.contributionAmt()).signum()>0;
            String share=known&&gain?ratio(g.contributionAmt(),positive.toPlainString(),true):null;
            return new Group(g.groupKind(),g.groupId(),g.groupName(),g.other(),g.salesAmt(),g.allocatedLaborCostAmt(),g.contributionAmt(),g.revenueShare(),g.contributionRate(),share,g.loss(),share!=null);
        }).toList();
        return new Composition(true,source.reason(),source.complete(),source.revenueDifference(),groups,source.customers(),source.salesParts());
    }
}
