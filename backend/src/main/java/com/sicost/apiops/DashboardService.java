package com.sicost.apiops;

import com.sicost.businessplansops.BusinessPlanPerformanceDtos.Target;
import com.sicost.cashflowops.CashFlowService;
import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.notificationsops.NotificationDtos.Link;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.apiops.DashboardDtos.*;
import static com.sicost.businessplansops.BusinessPlanActuals.*;
import static com.sicost.apiops.DashboardFinancialSources.amount;

@Service
public class DashboardService {
    private final DashboardReadRepository repository;
    private final DashboardFinancialSources financial;
    private final DashboardOperationalSources operations;
    private final CashFlowService cash;
    private final MonthCloseReadService closes;
    private final Clock clock;
    public DashboardService(DashboardReadRepository repository,DashboardFinancialSources financial,DashboardOperationalSources operations,
        CashFlowService cash,MonthCloseReadService closes,Clock clock) {
        this.repository=repository;this.financial=financial;this.operations=operations;this.cash=cash;this.closes=closes;this.clock=clock;
    }
    private record Context(YearMonth selected,YearMonth current,LocalDate day,OffsetDateTime at) {}
    private record Section<T>(T value,Basis basis) {}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Data getDashboard(String requested) {
        OffsetDateTime at=OffsetDateTime.now(clock.withZone(TimeConfiguration.SEOUL));YearMonth current=YearMonth.from(at);
        YearMonth base=parseBase(repository.base()),selected=requested==null?current:ProjectAnalysisInput.month(requested);
        if(selected.isBefore(base)||selected.isAfter(current))throw new ApiException(400,"INVALID_PERIOD","운영 시작월부터 현재월까지 선택해 주세요.","month",null);
        Context c=new Context(selected,current,at.toLocalDate(),at);
        var closeRows=section(c,selected,"MonthClose",()->repository.closes(db(base),db(current)));
        String last=closeRows.value()==null?null:closeRows.value().stream().filter(r->r.status().equals("CLOSE")).map(r->api(r.month())).max(String::compareTo).orElse(null);
        String closeStatus=closeRows.value()==null?"UNCONFIRMED":closeRows.value().stream().filter(r->r.month().equals(db(selected))).map(com.sicost.shared.SharedQueryDtos.CloseRow::status).findFirst().orElse("WORK");
        var pl=section(c,selected,"OperatingProfit",()->financial.read(selected));
        var targets=section(c,selected,"BusinessPlan",()->repository.targets(selected.getYear()));
        var receivables=section(c,current,"Receivables",()->repository.receivables(c.day()));
        var tax=section(c,current,"CorporateTax",()->repository.taxes(c.day()));
        var project=section(c,current,"ProjectCost/ProjectStatus",()->operations.projects(c.day()));
        var forecast=section(c,current,"WorkforceForecast/SalesPipeline",()->operations.forecast(c.day()));
        var withdrawal=section(c,current,"Withdrawal",()->operations.withdrawals(c.day()));
        var close=section(c,selected,"MonthClose",()->closes.getMonthCloses(new MonthCloseReadDtos.Query(selected.toString(),1,20)));
        YearMonth cashFrom=last==null?current:YearMonth.parse(last).plusMonths(1);if(cashFrom.isBefore(current))cashFrom=current;
        YearMonth start=cashFrom;
        var cashRows=section(c,current,"CashFlow",()->cash.calculateContiguousRange(start,3));
        var minimum=section(c,current,"OperatingStandards",()->repository.standard("MIN_CASH",current));
        var shortPeople=section(c,current,"OperatingStandards",()->repository.standard("SHORT_PERSON",current));
        var mmLimit=section(c,current,"OperatingStandards",()->repository.standard("MM_OVER",current));
        var targetUtil=section(c,selected,"OperatingStandards",()->repository.standard("TGT_UTIL_RT",selected));
        Map<String,Target> plans=new HashMap<>();if(targets.value()!=null)targets.value().forEach(t->plans.put(api(t.month()),t));
        var actual=pl.value()==null?null:pl.value().all().get(selected.toString());
        var before=pl.value()==null?null:pl.value().all().get(selected.minusMonths(1).toString());
        String sales=amount(actual,"salesAmt"),profit=amount(actual,"oprtPrftAmt"),previousSales=amount(before,"salesAmt"),previousProfit=amount(before,"oprtPrftAmt");
        Target target=plans.get(selected.toString());String salesTarget=target==null?null:money(target.sales());
        String ytd=pl.value()==null?null:sum(pl.value().months().stream().filter(m->m.month().compareTo(selected.toString())<=0).toList(),m->m.amounts().get("oprtPrftAmt"));
        String ytdTarget="0";for(int i=1;i<=selected.getMonthValue();i++){Target t=plans.get(YearMonth.of(selected.getYear(),i).toString());ytdTarget=t==null?null:add(ytdTarget,money(t.profit()));}
        var utilization=pl.value()==null?null:financial.utilization(pl.value(),selected);
        String util=utilization==null?null:utilization.value(),utilTarget=decimal(targetUtil.value());
        var r=receivables.value();
        List<Metric> metrics=List.of(
            metric("SALES",sales,previousSales,salesTarget,pl.basis(),"UI-08"),
            new Metric("OPERATING_PROFIT",profit,"KRW",previousProfit,subtract(profit,previousProfit),change(profit,previousProfit),null,null,ratio(profit,sales,false),null,null,null,null,valueBasis(pl.basis(),profit),link("UI-08",selected.toString())),
            metric("YTD_OPERATING_PROFIT",ytd,null,ytdTarget,pl.basis(),"UI-18"),
            new Metric("UTILIZATION",util,"PERCENT",null,null,null,utilTarget,null,null,oneDifference(util,utilTarget),utilization==null?null:utilization.idle(),null,null,valueBasis(pl.basis(),util),link("UI-28",selected.toString())),
            new Metric("RECEIVABLE",r==null?null:money(r.balance()),"KRW",null,null,null,null,null,null,null,null,r==null?null:r.overdueCount(),r==null?null:money(r.overdueAmount()),receivables.basis(),link("UI-13",null)));
        List<Trend> trend=new ArrayList<>();for(int i=1;i<=12;i++) {
            String month=YearMonth.of(selected.getYear(),i).toString();var a=pl.value()==null?null:pl.value().all().get(month);Target t=plans.get(month);
            trend.add(new Trend(month,i>selected.getMonthValue()?null:amount(a,"salesAmt"),i>selected.getMonthValue()?null:amount(a,"salesCostAmt"),t==null?null:money(t.sales())));
        }
        List<Supply> supply=new ArrayList<>();for(int i=1;i<=6;i++) {
            String month=current.plusMonths(i).toString();var row=forecast.value()==null?null:forecast.value().workforce().totals().stream().filter(m->m.month().equals(month)).findFirst().orElse(null);
            supply.add(new Supply(month,row==null?null:row.demand(),row==null?null:row.supply(),row==null?null:row.difference()));
        }
        List<Cash> cashForecast=new ArrayList<>();for(int i=0;i<3;i++) {
            String month=start.plusMonths(i).toString();var row=cashRows.value()==null?null:cashRows.value().stream().filter(m->m.month().equals(month)).findFirst().orElse(null);
            List<String> reasons=row==null?List.of("자금 원천을 확인하지 못했습니다."):row.issues().stream().map(com.sicost.cashflowops.CashFlowDtos.Issue::message).distinct().toList();
            if(minimum.value()==null){var all=new ArrayList<>(reasons);all.add("최소자금 기준 미확인");reasons=List.copyOf(all);}
            cashForecast.add(new Cash(month,row==null?null:row.closingBalance(),money(minimum.value()),row==null||row.closingBalance()==null||minimum.value()==null?null:row.shortage(),reasons,link("UI-21",month)));
        }
        var closeValue=close.value();
        List<Check> remaining=closeValue==null?Collections.emptyList():closeValue.checks().stream().filter(ch->!Set.of("OK","PASS").contains(ch.status())).map(ch->new Check(ch.code(),ch.status(),ch.count()==null?null:Math.toIntExact(ch.count()),ch.description())).toList();
        CloseProgress progress=new CloseProgress(close.basis(),closeStatus,closeValue==null?null:6-remaining.size(),6,remaining,"CLOSE".equals(closeStatus),link("UI-14",selected.toString()));
        List<Alert> alerts=alerts(project,c);
        var parts=pl.value()==null?Collections.<Part>emptyList():financial.parts(pl.value(),selected);
        var actions=new ArrayList<Action>();
        if(r!=null)addAction(actions,"OVERDUE_RECEIVABLE",r.overdueCount(),"UI-13",receivables.basis(),money(r.overdueAmount()));
        if(tax.value()!=null)addAction(actions,"CORPORATE_TAX_DUE",tax.value().count(),"UI-10",tax.basis(),null);
        var shortCash=cashForecast.stream().filter(row->Boolean.TRUE.equals(row.shortage())).toList();
        if(!shortCash.isEmpty())actions.add(new Action("CASH_SHORTAGE",shortCash.size(),true,null,null,shortCash.stream().map(Cash::month).toList(),null,null,"미마감 월말잔액이 최소자금보다 적습니다.",cashRows.basis(),link("UI-21",shortCash.get(0).month())));
        var shortSupply=supply.stream().filter(row->row.differencePersons()!=null&&shortPeople.value()!=null&&new BigDecimal(row.differencePersons()).compareTo(shortPeople.value().negate())<0).toList();
        if(!shortSupply.isEmpty())actions.add(new Action("STAFF_SHORTAGE",shortSupply.size(),true,null,shortSupply.stream().map(row->new BigDecimal(row.differencePersons()).abs()).max(BigDecimal::compareTo).orElseThrow().setScale(1,RoundingMode.HALF_UP).toPlainString(),shortSupply.stream().map(Supply::month).toList(),null,null,"가용 인력보다 수요가 많습니다.",forecast.basis(),link("UI-28",shortSupply.get(0).month())));
        if(project.value()!=null)projectActions(actions,project.value(),mmLimit.value(),c,project.basis());
        if(withdrawal.value()!=null){var w=withdrawal.value();if(w.withinThreeMonthsPeople()>0)actions.add(new Action("WITHDRAWAL_DUE",w.withinThreeMonthsPeople(),false,null,null,List.of(current.toString()),w.withoutNextAssignmentPeople(),w.withMatchingPositionsPeople(),w.message(),withdrawal.basis(),link("UI-05",null)));}
        if(closeValue!=null){var warning=closeValue.checks().stream().filter(ch->ch.code().equals("BILL_MISS")&&ch.count()!=null).findFirst();warning.ifPresent(ch->addAction(actions,"BILLING_MISSING","WARN".equals(ch.status())?Math.toIntExact(ch.count()):0,"UI-13",close.basis(),null));}
        if(forecast.value()!=null)addAction(actions,"QUOTE_APPROVAL_WAIT",(int)forecast.value().sources().opportunities().stream().filter(o->Set.of("ING","WAIT").contains(o.status())).count(),"UI-29",forecast.basis(),null);
        List<String> order=List.of("OVERDUE_RECEIVABLE","CORPORATE_TAX_DUE","CASH_SHORTAGE","STAFF_SHORTAGE","MM_OVER","PRECONTRACT_ASSIGNMENT","WITHDRAWAL_DUE","SALES_PART_MISSING","PROJECT_RISK","ISSUE_OVERDUE","WEEK_MEMO_STALE","BILLING_MISSING","QUOTE_APPROVAL_WAIT");
        actions.sort(Comparator.comparingInt(a->order.indexOf(a.actionCode())));
        boolean actionComplete=List.of(receivables.basis(),tax.basis(),project.basis(),forecast.basis(),withdrawal.basis(),close.basis(),cashRows.basis(),minimum.basis(),shortPeople.basis(),mmLimit.basis()).stream().allMatch(b->b.status().equals("AVAILABLE"))&&minimum.value()!=null&&shortPeople.value()!=null&&mmLimit.value()!=null&&cashForecast.stream().allMatch(row->row.shortage()!=null);
        Basis actionsBasis=new Basis(current.toString(),c.day(),"Receivables/CorporateTax/CashFlow/Workforce/Project/MonthClose",at,actionComplete?(actions.isEmpty()?"EMPTY":"AVAILABLE"):"UNCONFIRMED",actionComplete?List.of():List.of("일부 할일 원천 미확인: 알려진 항목만 표시합니다."));
        Basis cashBasis=cashRows.basis();if(cashForecast.stream().anyMatch(row->row.closingBalance()==null||row.minimumCashAmount()==null))cashBasis=valueBasis(cashBasis,null);
        return new Data(actionsBasis,project.basis(),selected.toString(),c.day(),current.toString(),base.toString(),last,closeStatus,!"CLOSE".equals(closeStatus),at,"ALL",metrics,List.copyOf(actions),actionComplete?actions.size():null,actionComplete?(int)actions.stream().filter(Action::urgent).count():null,progress,alerts,
            pl.basis(),List.copyOf(trend),forecast.basis(),List.copyOf(supply),pl.basis(),parts,"프로젝트 인식매출과 전체 프로젝트 원가 기준입니다. 판관비·대기비는 임의 안분하지 않습니다.",pipeline(forecast,c),cashBasis,List.copyOf(cashForecast));
    }
    private <T> Section<T> section(Context c,YearMonth month,String source,Supplier<T> read) {
        var result=repository.section(read);return new Section<>(result.value(),new Basis(month.toString(),c.day(),source,c.at(),result.failed()?"FAILED":"AVAILABLE",result.failed()?List.of("원천 조회에 실패했습니다. 재조회해 주세요."):List.of()));
    }
    private static Basis valueBasis(Basis b,String value){return value!=null||b.status().equals("FAILED")?b:new Basis(b.basisMonth(),b.basisDate(),b.sourceService(),b.observedAt(),"UNCONFIRMED",List.of("해당 지표 원천 미확인"));}
    private static Metric metric(String code,String value,String before,String target,Basis basis,String screen){return new Metric(code,value,"KRW",before,subtract(value,before),change(value,before),target,ratio(value,target,true),null,null,null,null,null,valueBasis(basis,value),link(screen,basis.basisMonth()));}
    static String change(String value,String previous){return previous==null||new BigDecimal(previous).signum()<=0?null:ratio(subtract(value,previous),previous,true);}
    private static String oneDifference(String a,String b){return a==null||b==null?null:new BigDecimal(a).subtract(new BigDecimal(b)).setScale(1,RoundingMode.HALF_UP).toPlainString();}
    private static String decimal(BigDecimal value){return value==null?null:value.setScale(1,RoundingMode.HALF_UP).toPlainString();}
    private static Link link(String screen,String key){return new Link(screen,key);}
    private static String db(YearMonth month){return month.toString().replace("-","");}
    private static YearMonth parseBase(String value){if(value==null)throw new IllegalStateException("운영 시작월이 없습니다.");return YearMonth.parse(value.contains("-")?value:api(value));}
    private static void addAction(List<Action> actions,String code,int count,String screen,Basis basis,String amount) {
        if(count>0)actions.add(new Action(code,count,Set.of("OVERDUE_RECEIVABLE","CORPORATE_TAX_DUE","CASH_SHORTAGE","STAFF_SHORTAGE").contains(code),amount,null,List.of(basis.basisMonth()),null,null,"원천 화면에서 상세 내용을 확인해 주세요.",basis,link(screen,basis.basisMonth())));
    }
    @SuppressWarnings("unchecked") private static List<Alert> alerts(Section<DashboardOperationalSources.Projects> projects,Context c) {
        if(projects.value()==null)return Collections.emptyList();List<Alert> result=new ArrayList<>();
        for(var row:projects.value().costs()) {
            if("IN".equals(row.get("contractType")))continue;
            for(var alert:(List<Map<String,Object>>)row.get("alerts"))result.add(alert(row,"BUDGET_OVER".equals(alert.get("kind"))?"BUDGET_EXCEEDED":"LOW_MARGIN",Objects.toString(alert.get("primaryCause"),Objects.toString(alert.get("message"))),projects.basis()));
            if(Boolean.TRUE.equals(row.get("budgetWarning")))result.add(alert(row,"BUDGET_WARNING","누적 원가가 예산 경고 기준에 도달했습니다.",projects.basis()));
        }
        return List.copyOf(result);
    }
    private static Alert alert(Map<String,Object> row,String type,String reason,Basis basis){return new Alert(row.get("projectId").toString(),row.get("projectName").toString(),row.get("contractType").toString(),type,reason,(String)("TK".equals(row.get("contractType"))?row.get("forecastMargin"):row.get("monthlyMargin")),(String)row.get("budgetUsage"),basis,link("UI-04",row.get("projectId").toString()));}
    private static void projectActions(List<Action> actions,DashboardOperationalSources.Projects p,BigDecimal limit,Context c,Basis basis) {
        String month=db(c.current());var source=p.snapshot().sources();Map<String,BigDecimal> mm=new HashMap<>();Set<String> pre=new HashSet<>();
        for(var person:p.snapshot().costs().items())if(person.month().equals(month)){mm.put(person.personId(),new BigDecimal(person.assignmentMm()));if(person.rows().stream().anyMatch(r->"PRE".equals(r.costClass())&&r.assignmentMm()!=null&&new BigDecimal(r.assignmentMm()).signum()>0))pre.add(person.personId());}
        if(limit!=null)addAction(actions,"MM_OVER",(int)mm.values().stream().filter(v->v.compareTo(limit)>0).count(),"UI-12",basis,null);
        addAction(actions,"PRECONTRACT_ASSIGNMENT",pre.size(),"UI-12",basis,null);
        addAction(actions,"SALES_PART_MISSING",(int)p.board().stream().filter(row->!"IN".equals(row.get("contractType"))&&row.get("partId")==null).count(),"UI-01",basis,null);
        addAction(actions,"PROJECT_RISK",(int)p.board().stream().filter(row->"위험".equals(row.get("light"))).count(),"UI-31",basis,null);
        addAction(actions,"ISSUE_OVERDUE",p.board().stream().mapToInt(row->((Number)row.get("overdueIssueCount")).intValue()).sum(),"UI-31",basis,null);
        addAction(actions,"WEEK_MEMO_STALE",(int)p.board().stream().filter(row->row.get("lastUpdatedDate")==null||((LocalDate)row.get("lastUpdatedDate")).isBefore(c.day().minusDays(14))).count(),"UI-31",basis,null);
    }
    private static Pipeline pipeline(Section<DashboardOperationalSources.Forecast> forecast,Context c) {
        if(forecast.value()==null)return new Pipeline(forecast.basis(),null,null,Collections.emptyList());
        var opportunities=forecast.value().sources().opportunities().stream().filter(o->"ING".equals(o.status())).toList();
        BigDecimal weighted=BigDecimal.ZERO;boolean complete=true;
        for(var o:opportunities){if(o.amount()==null||o.probability()==null){complete=false;continue;}weighted=weighted.add(o.amount().multiply(o.probability()).divide(BigDecimal.valueOf(100),0,RoundingMode.DOWN));}
        var soon=opportunities.stream().filter(o->o.startMonth()!=null&&o.probability()!=null&&o.amount()!=null).filter(o->api(o.startMonth()).compareTo(c.current().toString())>=0&&api(o.startMonth()).compareTo(c.current().plusMonths(3).toString())<=0)
            .sorted(Comparator.comparing(com.sicost.forecast.SalesForecastSources.Opportunity::probability).reversed().thenComparing(com.sicost.forecast.SalesForecastSources.Opportunity::startMonth).thenComparing(com.sicost.forecast.SalesForecastSources.Opportunity::id)).limit(3)
            .map(o->new Opportunity(o.id(),o.name(),api(o.startMonth()),decimal(o.probability()),money(o.amount()),link("UI-29",o.id()))).toList();
        return new Pipeline(complete?forecast.basis():valueBasis(forecast.basis(),null),opportunities.size(),complete?money(weighted):null,soon);
    }
}
