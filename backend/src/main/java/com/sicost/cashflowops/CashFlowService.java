package com.sicost.cashflowops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.excel.ExcelExportService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.cashflowops.CashFlowDtos.*;
import static com.sicost.cashflowops.CashFlowContributions.*;
import static com.sicost.common.financial.FinancialCalculation.money;

@Service("cashFlowEvidenceService")
public class CashFlowService {
    private final CashFlowRepository repository;
    private final CurrentUserProvider users;
    private final Clock clock;
    private final ExcelExportService excel;
    public CashFlowService(CashFlowRepository repository,CurrentUserProvider users,Clock clock,ExcelExportService excel) {
        this.repository=repository;this.users=users;this.clock=clock;this.excel=excel;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public SourcesPage getCashFlowSources(SourceQuery query) {
        users.currentUser();YearMonth month=YearMonth.parse(query.month());
        scope(month,repository.base(),YearMonth.of(today().getYear(),12),"month");
        var snapshot=repository.load(month.getYear());
        List<Source> all=calculate(snapshot,month,YearMonth.from(today()),repository.financial(query.month()),repository.receipts(query.month()))
            .stream().filter(s->s.item()==query.item()).toList();
        long start=((long)query.page()-1)*query.size();
        List<Source> page=start>=all.size()?List.of():all.subList((int)start,(int)Math.min(start+query.size(),all.size()));
        return new SourcesPage(query.month(),query.item(),page,all.size(),query.page(),query.size(),start+page.size()<all.size());
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Result calculateCashFlow(Query query){return annual(query).result();}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<Month> calculateContiguousRange(YearMonth from,int count) {
        if(count<1||count>3||from.isBefore(repository.base()))throw period("month");
        Map<Integer,Result> years=new HashMap<>();List<Month> result=new ArrayList<>();
        for(int i=0;i<count;i++) {
            YearMonth month=from.plusMonths(i);
            Result year=years.computeIfAbsent(month.getYear(),y->annual(new Query(y,Unit.WON),null,true).result());
            result.add(year.months().stream().filter(m->m.month().equals(month.toString())).findFirst().orElseThrow());
        }
        return List.copyOf(result);
    }
    public record CloseImpact(Month before,Month after) {}
    /** Same ledger and source snapshot as the annual screen; the CLOSE is hypothetical only. */
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public CloseImpact calculateCloseImpact(String month) {
        YearMonth selected=YearMonth.parse(month);
        var query=new Query(selected.getYear(),Unit.WON);
        var before=annual(query).result().months().stream().filter(m->month.equals(m.month())).findFirst().orElseThrow();
        var after=annual(query,month).result().months().stream().filter(m->month.equals(m.month())).findFirst().orElseThrow();
        return new CloseImpact(before,after);
    }
    private record Annual(Result result,List<Source> sources) {}
    private Annual annual(Query query) {
        return annual(query,null);
    }
    private Annual annual(Query query,String closingMonth) {
        return annual(query,closingMonth,false);
    }
    private Annual annual(Query query,String closingMonth,boolean internalRange) {
        users.currentUser();LocalDate today=today();int year=query.year()==null?today.getYear():query.year();
        YearMonth base=repository.base();
        if(year<base.getYear()||year>(internalRange?today.getYear()+1:today.getYear()))throw period("year");
        var snapshot=repository.load(year);YearMonth current=YearMonth.from(today);
        if(closingMonth!=null) {
            String db=closingMonth.replace("-","");
            var closes=new ArrayList<>(snapshot.closes().stream().filter(c->!db.equals(c.month())).toList());
            closes.add(new com.sicost.shared.SharedQueryDtos.CloseRow(db,"CLOSE"));
            snapshot=new CashFlowRepository.Snapshot(snapshot.base(),snapshot.people(),snapshot.payroll(),snapshot.standards(),List.copyOf(closes),snapshot.rates());
        }
        BigDecimal baseAmount=required(standard(snapshot,"CASH_BASE_AMT",current));
        BigDecimal minimum=required(standard(snapshot,"MIN_CASH",current));
        BigDecimal opening=baseAmount;List<Month> months=new ArrayList<>();List<Source> evidence=new ArrayList<>();
        for(YearMonth m=base;!m.isAfter(YearMonth.of(year,12));m=m.plusMonths(1)) {
            List<Source> sources=calculate(snapshot,m,current,repository.financial(m.toString()),repository.receipts(m.toString()));
            List<ItemAmount> items=items(sources);BigDecimal income=total(items,true),expense=total(items,false),balance=subtract(sum(opening,income),expense);
            List<Issue> issues=sources.stream().flatMap(s->s.issues().stream()).toList();
            boolean closed=closed(snapshot,m),shortage=!closed&&balance!=null&&balance.compareTo(minimum)<0;
            if(m.getYear()==year){months.add(new Month(m.toString(),closed?"ACTUAL":"PLANNED",money(opening),items,money(income),money(expense),money(balance),balance!=null,shortage,issues));evidence.addAll(sources);}
            opening=balance;
        }
        for(int month=1;month<=12;month++) {
            YearMonth m=YearMonth.of(year,month);
            if(m.isBefore(base))months.add(new Month(m.toString(),"BEFORE_BASE",null,Arrays.stream(Item.values()).map(i->new ItemAmount(i,null,true,false)).toList(),null,null,null,false,false,List.of()));
        }
        months.sort(Comparator.comparing(Month::month));
        List<PeriodTotal> totals=List.of(periodTotal("FIRST_HALF",months.subList(0,6)),periodTotal("SECOND_HALF",months.subList(6,12)),periodTotal("YEAR",months));
        List<ChartPoint> points=months.stream().map(m->new ChartPoint(m.month(),m.closingBalance(),m.status(),m.shortage())).toList();
        return new Annual(new Result(year,query.unit(),today,"Asia/Seoul",base.toString(),money(baseAmount),money(minimum),
            standardMonth(snapshot,"MIN_CASH",current),List.copyOf(months),totals,points,points.stream().filter(ChartPoint::shortage).toList(),
            months.stream().flatMap(m->m.issues().stream()).toList()),List.copyOf(evidence));
    }
    public ExcelExportService.Download getCashFlowExport(Query query,HttpServletRequest request) {
        return excel.generateWorkbook(new ExcelExportService.Specification("자금수지표",null,
            List.of(new ExcelExportService.Column("item","항목",ExcelExportService.CellType.TEXT)),"현금표",query.unit().label,"UI-21"),
            ()->{Annual annual=annual(query);return workbook(annual.result(),annual.sources());},request);
    }
    static ExcelExportService.WorkbookData workbook(Result r,List<Source> sources) {
        var text=ExcelExportService.CellType.TEXT;var number=ExcelExportService.CellType.NUMBER;
        List<ExcelExportService.Column> columns=new ArrayList<>();columns.add(new ExcelExportService.Column("item","항목",text));
        r.months().forEach(m->columns.add(new ExcelExportService.Column(m.month(),m.month()+" "+m.status(),number)));
        r.periodTotals().forEach(p->columns.add(new ExcelExportService.Column(p.period(),p.period(),number)));
        List<List<Object>> rows=new ArrayList<>();
        rows.add(row("전월이월금",r,Month::openingBalance,PeriodTotal::openingBalance));
        rows.add(row("수입",r,m->null,p->null));
        for(Item i:Item.values())if(i.ordinal()<3)rows.add(row(label(i),r,m->amount(m.items(),i),p->amount(p.items(),i)));
        rows.add(row("수입 합계",r,Month::incomeTotal,PeriodTotal::incomeTotal));
        rows.add(row("지출",r,m->null,p->null));
        for(Item i:Item.values())if(i.ordinal()>=3)rows.add(row(label(i),r,m->amount(m.items(),i),p->amount(p.items(),i)));
        rows.add(row("지출 합계",r,Month::expenseTotal,PeriodTotal::expenseTotal));
        rows.add(row("월말 잔액",r,Month::closingBalance,PeriodTotal::closingBalance));
        List<ExcelExportService.Column> sourceColumns=List.of(new ExcelExportService.Column("sourceType","원천종류",text),new ExcelExportService.Column("sourceId","원천ID",text),
            new ExcelExportService.Column("date","귀속일",ExcelExportService.CellType.DATE),new ExcelExportService.Column("sourceMonth","기준월",text),
            new ExcelExportService.Column("month","귀속월",text),new ExcelExportService.Column("item","항목",text),new ExcelExportService.Column("status","실적/예정/미확인",text),
            new ExcelExportService.Column("amount","금액",number),new ExcelExportService.Column("reference","참조급여",text),new ExcelExportService.Column("type","직원구분",text),
            new ExcelExportService.Column("formula","계산근거",text),new ExcelExportService.Column("issues","미확인 사유",text));
        List<ExcelExportService.Sheet> sheets=List.of(new ExcelExportService.Sheet("자금수지표",columns,sink->rows.forEach(sink)),
            new ExcelExportService.Sheet("원천근거",sourceColumns,sink->sources.forEach(s->sink.accept(Arrays.asList(s.sourceType(),s.sourceId(),s.attributionDate(),s.sourceMonth(),s.attributionMonth(),s.item().name(),s.status(),display(s.amount(),r.unit()),s.referencePayrollMonth(),s.employeeType(),s.formula(),s.issues().stream().map(Issue::message).reduce((a,b)->a+" · "+b).orElse(""))))));
        return new ExcelExportService.WorkbookData(sheets,List.of(new ExcelExportService.Information("단위",r.unit().label),new ExcelExportService.Information("기준일",r.asOfDate().toString()),
            new ExcelExportService.Information("시작월",r.baseMonth()),new ExcelExportService.Information("기초잔액",r.baseAmount()),new ExcelExportService.Information("최소 운영자금",r.minimumCash()),
            new ExcelExportService.Information("최소자금 기준월",r.minimumCashMonth())),sources.size(),String.format("%04d",r.year()));
    }
    private static List<Object> row(String label,Result r,Function<Month,String> month,Function<PeriodTotal,String> total) {
        List<Object> result=new ArrayList<>();result.add(label);r.months().forEach(m->result.add(display(month.apply(m),r.unit())));r.periodTotals().forEach(p->result.add(display(total.apply(p),r.unit())));return result;
    }
    private static BigDecimal display(String value,Unit unit){return value==null?null:new BigDecimal(value).movePointLeft(unit.scale);}
    private static List<ItemAmount> items(List<Source> sources) {
        return Arrays.stream(Item.values()).map(i->{BigDecimal total=BigDecimal.ZERO;for(Source s:sources)if(s.item()==i)total=sum(total,s.amount()==null?null:new BigDecimal(s.amount()));return new ItemAmount(i,money(total),true,total!=null);}).toList();
    }
    private static String amount(List<ItemAmount> items,Item item){return items.stream().filter(i->i.item()==item).findFirst().orElseThrow().amount();}
    private static BigDecimal total(List<ItemAmount> items,boolean income){BigDecimal total=BigDecimal.ZERO;for(ItemAmount i:items)if((i.item().ordinal()<3)==income)total=sum(total,i.amount()==null?null:new BigDecimal(i.amount()));return total;}
    private static PeriodTotal periodTotal(String period,List<Month> input) {
        List<Month> months=input.stream().filter(m->!m.status().equals("BEFORE_BASE")).toList();
        List<ItemAmount> items=Arrays.stream(Item.values()).map(i->{BigDecimal total=months.isEmpty()?null:BigDecimal.ZERO;for(Month m:months)total=sum(total,decimal(amount(m.items(),i)));return new ItemAmount(i,money(total),true,total!=null);}).toList();
        return new PeriodTotal(period,months.isEmpty()?null:months.get(0).openingBalance(),items,
            aggregate(months,Month::incomeTotal),aggregate(months,Month::expenseTotal),months.isEmpty()?null:months.get(months.size()-1).closingBalance());
    }
    private static String aggregate(List<Month> months,Function<Month,String> field){BigDecimal n=months.isEmpty()?null:BigDecimal.ZERO;for(Month m:months)n=sum(n,decimal(field.apply(m)));return money(n);}
    private static BigDecimal decimal(String value){return value==null?null:new BigDecimal(value);}
    public static String label(Item item){return switch(item){case CASH_SALES->"현금 매출";case MISC->"잡수익";case LOAN->"대여금";case REG_PAY->"정규직 급여";case CON_FL_PAY->"계약직·프리랜서 급여";case OS_PAY->"외주비";case UNPAID_LABOR->"미지급 인건비";case EXPENSE->"제경비";case CORP_TAX->"법인세";};}
    private LocalDate today(){return LocalDate.now(clock.withZone(TimeConfiguration.SEOUL));}
    private static BigDecimal required(BigDecimal n){if(n==null)throw new IllegalStateException("필수 운영 기준이 없습니다.");return n;}
    private static void scope(YearMonth value,YearMonth from,YearMonth to,String field){if(value.isBefore(from)||value.isAfter(to))throw period(field);}
    private static ApiException period(String field){return new ApiException(409,"PERIOD_OUT_OF_SCOPE","운영 시작월부터 서울 현재연도 12월까지 조회할 수 있습니다.",field,null);}
}
