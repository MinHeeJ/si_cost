package com.sicost.salespartsettlements;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.common.validation.ProjectInputPolicy;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.salespartsettlements.SalesPartSettlementDtos.*;
import static com.sicost.salespartsettlements.SalesPartSettlementCalculator.*;
import static com.sicost.salespartsettlements.SalesPartSettlementRepository.Snapshot;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class SalesPartSettlementService {
    private final SalesPartSettlementRepository repository;
    private final SalesPartSettlementSources sources;
    private final SalesPartSettlementSnapshotReader snapshots;
    private final RevisionService revisions;
    public SalesPartSettlementService(SalesPartSettlementRepository repository,SalesPartSettlementSources sources,
        SalesPartSettlementSnapshotReader snapshots,RevisionService revisions) {
        this.repository=repository;this.sources=sources;this.snapshots=snapshots;this.revisions=revisions;
    }
    public TabsResult getSalesPartSettlements(SalesPartSettlementInput.Query query) {return tabs(repository.load(query.month(),false));}
    public TabsResult listEligibleParts(String month) {return getSalesPartSettlements(new SalesPartSettlementInput.Query(month,1,50,"name","ASC"));}
    public Result previewSettlement(String partId,String month) {return read(partId,new SalesPartSettlementInput.Query(month,1,100,"name","ASC"),false);}
    public Result getSalesPartSettlement(String partId,SalesPartSettlementInput.Query query) {
        return read(partId,query,true);
    }
    private Result read(String partId,SalesPartSettlementInput.Query query,boolean paginate) {
        ProjectInputPolicy.text(partId,20,"partId");
        Snapshot snapshot=repository.load(query.month(),true);
        TabsResult tabs=tabs(snapshot);String month=tabs.month();String db=DatabaseMonthCodec.toDatabase(month);
        PartRow part=snapshot.parts().stream().filter(p->p.partId().equals(partId)&&!"Y".equals(p.headquarters())).findFirst().orElseThrow(SalesPartSettlementService::notFound);
        Tab tab=tabs.parts().stream().filter(t->t.partId().equals(partId)).findFirst().orElse(null);
        if(tab==null) {
            Standard standard=standard(snapshot,partId,db);
            tab=new Tab(partId,part.name(),false,false,"WORK",standard,List.of(issue("PART_NOT_ELIGIBLE","선택월 정산 대상 파트가 아닙니다.","partId",null,month,"TB_BS_PART",true)));
        }
        Parent parent=parent(snapshot,partId);boolean confirmed=parent!=null&&parent.confirmed();
        boolean closed=snapshot.sources().protectedMonth(db);
        boolean pastYear=!month.substring(0,4).equals(tabs.currentMonth().substring(0,4));
        List<Issue> issues=new ArrayList<>(tab.issues());List<String> reasons=new ArrayList<>();
        if(pastYear)reasons.add("과거 연도는 조회 전용입니다.");
        if(closed)reasons.add("마감된 월은 조회 전용입니다.");
        if(!tab.eligible())reasons.add(tab.historyOnly()?"정산 대상이 아닌 확정 이력입니다.":"선택월 정산 대상 파트가 아닙니다.");
        List<Detail> details=snapshot.details().stream().filter(d->d.partId().equals(partId)).toList();
        List<Row> all;
        if(confirmed)all=snapshots.read(details,month);
        else if(closed||!tab.eligible()) {
            all=List.of();
            issues.add(issue(closed?"CLOSED_SNAPSHOT_MISSING":"PART_NOT_ELIGIBLE",closed?"마감 정산 자료 미확인":"정산 대상이 아닙니다.",null,null,month,"TB_CO_PART_SETL",true));
        } else all=sources.calculate(snapshot,tab,issues);
        String card=parent==null?"0":money(parent.cardAmount());
        Totals totals=totals(all,card,parent==null?null:money(parent.partProfitAmount()),confirmed);
        if(!confirmed&&(closed||!tab.eligible()))totals=new Totals(0,0,null,null,null,null,null,null,null,null,card,null);
        if(confirmed&&(totals.personProfitShareAmount()==null||parent.partProfitAmount()==null||decimal(totals.personProfitShareAmount()).subtract(parent.cardAmount()).compareTo(parent.partProfitAmount())!=0))
            issues.add(issue("SNAPSHOT_MISMATCH","정산 사본 불일치", "partProfitAmount",null,month,"TB_CO_PART_SETL",false));
        if(confirmed&&closed&&details.isEmpty())issues.add(issue("CLOSED_SNAPSHOT_MISSING","마감 정산 자료 미확인",null,null,month,"TB_CO_PART_SETL_DTL",true));
        issues.addAll(all.stream().flatMap(r->r.issues().stream()).toList());
        boolean editable=!closed&&!pastYear&&tab.eligible();
        if(confirmed)reasons.add("확정된 정산 사본입니다.");
        String revision=revision(snapshot,partId,confirmed);
        List<Row> sorted=all.stream().sorted(comparator(query)).toList();
        long from=(long)(query.page()-1)*query.size();
        List<Row> page=!paginate?sorted:from>=sorted.size()?List.of():sorted.subList((int)from,(int)Math.min(from+query.size(),sorted.size()));
        return new Result(partId,month,confirmed?parent.status():"WORK",tab.standard(),card,revision,
            parent==null?null:parent.confirmedBy(),parent==null?null:parent.confirmedByName(),parent==null||parent.confirmedAt()==null?null:parent.confirmedAt().atZone(com.sicost.common.TimeConfiguration.SEOUL).toOffsetDateTime().toString(),parent==null?null:parent.cancellationReason(),
            !editable||confirmed,List.copyOf(reasons),editable&&!confirmed,editable&&!confirmed&&issues.stream().noneMatch(Issue::blocking),!closed&&!pastYear&&confirmed,
            totals,totals,List.copyOf(page),new Page(query.page(),query.size(),all.size()),List.copyOf(issues));
    }
    public TabsResult tabs(Snapshot snapshot) {
        String db=snapshot.sources().period().toMonth(),month=DatabaseMonthCodec.toApi(db);
        List<Tab> tabs=new ArrayList<>();List<Issue> issues=new ArrayList<>();int count=0,confirmedCount=0;
        for(PartRow part:snapshot.parts().stream().sorted(Comparator.comparing(PartRow::name).thenComparing(PartRow::partId)).toList()) {
            if("Y".equals(part.headquarters()))continue;
            Standard standard=standard(snapshot,part.partId(),db);Parent parent=parent(snapshot,part.partId());boolean confirmed=parent!=null&&parent.confirmed();
            boolean active=activeAtMonthEnd(part,snapshot.history(),db);
            boolean eligible=active&&standard.valid();List<Issue> problems=new ArrayList<>();
            if(active&&!standard.valid()) {var issue=issue("PART_STANDARD_UNKNOWN","파트 기준 미확인: 유효 기준 부재·중첩 또는 허용 범위를 확인해 주세요.","standard",null,month,"TB_BS_PART_STD",true);problems.add(issue);issues.add(issue);}
            if(eligible) {count++;if(confirmed)confirmedCount++;}
            if(eligible||confirmed)tabs.add(new Tab(part.partId(),part.name(),eligible,!eligible&&confirmed,parent==null?"WORK":parent.status(),standard,List.copyOf(problems)));
        }
        return new TabsResult(month,DatabaseMonthCodec.toApi(snapshot.sources().period().baseMonth()),YearMonth.from(snapshot.sources().asOfDate()).toString(),List.copyOf(tabs),count,confirmedCount,count-confirmedCount,List.copyOf(issues));
    }
    public static boolean activeAtMonthEnd(PartRow part,List<UseHistory> history,String month) {
        LocalDateTime end=com.sicost.profitability.MonthlyCostDtos.parseMonth(month).plusMonths(1).atDay(1).atStartOfDay();
        List<UseHistory> changes=history.stream().filter(h->h.partId().equals(part.partId())).sorted(Comparator.comparing(UseHistory::at).thenComparing(h->new BigDecimal(h.histId()))).toList();
        if(changes.isEmpty())return "Y".equals(part.useYn());
        String value=changes.get(0).beforeValue();
        for(UseHistory change:changes)if(change.at().isBefore(end))value=change.afterValue();
        return "Y".equals(value);
    }
    public static Standard standard(Snapshot snapshot,String id,String month) {
        List<StandardRow> rows=snapshot.standards().stream().filter(s->id.equals(s.partId())&&s.month().compareTo(month)<=0&&(s.endMonth()==null||s.endMonth().compareTo(month)>=0)).toList();
        if(rows.size()!=1)return new Standard(null,null,null,null,null,false);
        StandardRow row=rows.get(0);boolean valid=row.rate()!=null&&row.rate().signum()>=0&&row.rate().compareTo(new BigDecimal("100"))<=0&&row.expense()!=null&&row.expense().signum()>=0&&row.expense().compareTo(new BigDecimal("999999999999999"))<=0;
        return new Standard(row.leaderId(),row.leaderName(),valid?row.rate().toPlainString():null,valid?money(row.expense()):null,DatabaseMonthCodec.toApi(row.month()),valid);
    }
    private String revision(Snapshot snapshot,String partId,boolean confirmed) {
        Parent parent=parent(snapshot,partId);
        var identity=snapshot.parts().stream().filter(p->partId.equals(p.partId())).map(p->Arrays.asList(p.partId(),p.headquarters(),p.useYn(),p.regUserId(),p.regDttm(),p.modUserId(),p.modDttm())).toList();
        var stored=parent==null?null:Arrays.asList(parent.partId(),parent.month(),parent.cardAmount(),parent.partProfitAmount(),parent.status(),parent.confirmedBy(),parent.confirmedAt(),parent.cancellationReason(),parent.regUserId(),parent.regDttm(),parent.modUserId(),parent.modDttm());
        var detail=snapshot.details().stream().filter(d->partId.equals(d.partId())).map(d->Arrays.asList(d.partId(),d.month(),d.developerId(),d.employeeType(),d.projectText(),d.mm(),d.unitPrice(),d.pay(),d.expense(),d.retirement(),d.cost(),d.profit(),d.share(),d.remark(),d.regUserId(),d.regDttm(),d.modUserId(),d.modDttm())).toList();
        var references=snapshot.revisionSources().stream().filter(r->Set.of("TB_CL_CLOSE","CASH_BASE_YM").contains(r.source())).toList();
        return revisions.revision(confirmed?Arrays.asList(identity,stored,detail,references,snapshot.sources().closes(),snapshot.sources().period().baseMonth()):Arrays.asList(identity,stored,detail,snapshot.parts(),snapshot.standards(),snapshot.history(),snapshot.parents(),snapshot.details(),snapshot.revisionSources(),snapshot.sources().closes()));
    }
    private static Parent parent(Snapshot snapshot,String id) {return snapshot.parents().stream().filter(p->id.equals(p.partId())).findFirst().orElse(null);}
    private static Comparator<Row> comparator(SalesPartSettlementInput.Query query) {
        Function<Row,String> field=switch(query.sort()) {case "employeeType"->Row::employeeType;case "mm"->Row::mm;case "unitPrice"->Row::unitPrice;case "payAmount"->Row::payAmount;case "costAmount"->Row::costAmount;case "profitAmount"->Row::profitAmount;case "profitShareAmount"->Row::profitShareAmount;default->Row::name;};
        Comparator<String> value=Set.of("name","employeeType").contains(query.sort())?Comparator.naturalOrder():Comparator.comparing(BigDecimal::new);
        if("DESC".equals(query.direction()))value=value.reversed();
        return Comparator.comparing(field,Comparator.nullsLast(value)).thenComparing(Row::developerId);
    }
    private static ApiException notFound() {return new ApiException(404,"NOT_FOUND","일반 영업 파트를 찾을 수 없습니다.");}
}
