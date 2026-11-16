package com.sicost.analysis;

import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.structure.*;
import com.sicost.profitability.*;
import com.sicost.costanalysis.ProjectCostService;
import com.sicost.forecast.*;
import com.sicost.cashflowops.*;
import com.sicost.apiops.ReceivableService;
import com.sicost.salespartsettlements.*;
import com.sicost.bills.*;
import com.sicost.assignments.*;
import com.sicost.assignmentsops.AssignmentActionsDtos;
import com.sicost.salesopportunities.*;
import com.sicost.businessplansops.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.analysis.AnalysisAssistantDtos.*;
import static com.sicost.analysis.AnalysisQuestionInterpreter.Function.*;
import static com.sicost.businessplansops.BusinessPlanActuals.*;

/** Select-only integration. All business calculations are owned by their original services. */
@Service
public class AnalysisDataQueries {
    private final ProfitStructureRepository company;
    private final MonthlyCostService costs;
    private final IndividualProfitabilityService individuals;
    private final ProjectCostService projects;
    private final SalesRevenueForecastService revenue;
    private final SalesForecastRepository forecastSources;
    private final SalesWorkforceProjectionService workforce;
    private final CashFlowService cash;
    private final ReceivableService receivables;
    private final SalesPartSettlementService settlements;
    private final AnalysisAssistantMapper mapper;
    private final AnalysisAccessPolicy policy;
    private final RevisionService revisions;
    private final OperatingProfitForecastService profitForecast;
    private final BillService bills;
    private final AssignmentService assignments;
    private final SalesOpportunityService opportunities;
    private final BusinessPlanPerformanceService performance;
    public AnalysisDataQueries(ProfitStructureRepository company,MonthlyCostService costs,IndividualProfitabilityService individuals,
        ProjectCostService projects,SalesRevenueForecastService revenue,SalesForecastRepository forecastSources,SalesWorkforceProjectionService workforce,
        CashFlowService cash,ReceivableService receivables,SalesPartSettlementService settlements,AnalysisAssistantMapper mapper,AnalysisAccessPolicy policy,RevisionService revisions,OperatingProfitForecastService profitForecast,BillService bills,AssignmentService assignments,SalesOpportunityService opportunities,BusinessPlanPerformanceService performance) {
        this.company=company;this.costs=costs;this.individuals=individuals;this.projects=projects;this.revenue=revenue;this.forecastSources=forecastSources;
        this.workforce=workforce;this.cash=cash;this.receivables=receivables;this.settlements=settlements;this.mapper=mapper;this.policy=policy;this.revisions=revisions;this.profitForecast=profitForecast;this.bills=bills;this.assignments=assignments;this.opportunities=opportunities;this.performance=performance;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Result read(String user,Question question,AnalysisQuestionInterpreter.Intent intent,OffsetDateTime at) {
        Builder b=new Builder(user,question,intent,at);
        // A locally identified person/project is never silently answered as whole-company PL.
        if(intent.reason()==null&&intent.function()==COMPANY&&!question.question().matches("(?s).*(회사|전사).*")) {
            var people=mapper.people().stream().filter(e->mentions(question.question(),e)).toList();
            var projects=mapper.projects().stream().filter(e->mentions(question.question(),e)).toList();
            boolean here=question.question().matches("(?s).*이\\s*(개발자|프로젝트|화면).*" );
            if(!people.isEmpty()&&!projects.isEmpty())return b.noAnswer("개발자와 프로젝트가 함께 식별됩니다. 대상 종류와 번호를 다시 지정해 주세요.");
            var function=!people.isEmpty()||here&&question.context().developerId()!=null?PERSONAL:!projects.isEmpty()||here&&question.context().projectId()!=null?PROJECT:COMPANY;
            if(function!=COMPANY)b=new Builder(user,question,new AnalysisQuestionInterpreter.Intent(function,intent.from(),intent.to(),false,intent.minimumOverdue(),intent.reason()),at);
        }
        if(intent.reason()!=null)return b.noAnswer(intent.reason());
        if(question.context().projectId()!=null&&mapper.projects().stream().noneMatch(e->e.id().equals(question.context().projectId())))return b.noAnswer("해당 프로젝트가 없습니다. 대상을 다시 지정해 주세요.");
        if(question.context().developerId()!=null&&mapper.people().stream().noneMatch(e->e.id().equals(question.context().developerId())))return b.noAnswer("해당 개발자가 없습니다. 대상을 다시 지정해 주세요.");
        policy.require(user,b.scope());
        switch(b.i.function()) {
            case COMPANY -> company(b);
            case PERSONAL -> personal(b);
            case PROJECT,TURNKEY -> project(b);
            case REVENUE_FORECAST -> revenue(b);
            case WORKFORCE -> workforce(b);
            case CASH,CASH_FORECAST -> cash(b);
            case RECEIVABLE -> receivables(b);
            case BILLING -> billing(b);
            case ASSIGNMENTS -> assignments(b);
            case OPPORTUNITIES -> opportunities(b);
            case SETTLEMENT -> settlement(b);
            default -> {return b.noAnswer("지원하는 조회를 다시 지정해 주세요.");}
        }
        policy.require(user,b.scope());
        if(b.question.question().matches(".*(왜|원인|이유).*"))b.unknown.add("원인 판단은 등록 자료만으로 확정하지 않습니다. 요청한 수치 부분만 제공합니다.");
        return b.finish();
    }
    private void company(Builder b) {
        List<Column> cols=new ArrayList<>(List.of(col("month","기간","MONTH",""),col("state","자료 상태","TEXT","")));
        LinkedHashMap<String,String> selected=new LinkedHashMap<>();String q=b.question.question();
        Map<String,String> labels=new LinkedHashMap<>();labels.put("매출","salesAmt");labels.put("매출원가","salesCostAmt");labels.put("매출총이익","grossPrftAmt");labels.put("판관비","sgaTotAmt");labels.put("영업이익","oprtPrftAmt");labels.put("순이익","netPrftAmt");
        labels.forEach((label,key)->{if(q.contains(label))selected.put(key,label);});
        if(q.contains("율")||q.contains("률")){if(q.contains("영업"))selected.put("operatingProfitRate","영업이익률");if(q.contains("순이익"))selected.put("netProfitRate","순이익률");if(q.contains("총이익"))selected.put("grossProfitRate","매출총이익률");}
        if(selected.isEmpty()||q.contains("회사 실적")||q.contains("회사 손익"))labels.forEach((label,key)->selected.put(key,label));
        if(q.contains("목표")){companyTargets(b);return;}
        selected.forEach((key,label)->cols.add(col(key,label,"DECIMAL",key.endsWith("Rate")?"%":"원")));
        b.authorize(target("COMPANY","ALL","회사",new ArrayList<>(selected.keySet())));
        List<Row> rows=new ArrayList<>();
        for(int year=b.i.from().getYear();year<=b.i.to().getYear();year++) {
            YearMonth end=year==b.i.to().getYear()?b.i.to():YearMonth.of(year,12);
            var read=company.read(new ProfitStructureDtos.Query(year,end.toString(),"MONTH","원"));
            for(var month:read.months())if(inRange(month.month(),b.i)) {
                String state=month.migrated()?"MIGRATED":month.provisional()?"PROVISIONAL":month.fixed()?"CLOSED":"UNKNOWN";
                if(month.provisional())b.provisional=true;
                List<Cell> cells=new ArrayList<>(List.of(cell("month",month.month()),cell("state",state)));
                selected.forEach((key,label)->cells.add(cell(key,month.amounts().get(key))));
                rows.add(new Row(month.month(),target("COMPANY","ALL","회사",new ArrayList<>(selected.keySet())),cells,null));
                month.completeness().issues().forEach(issue->b.unknown.add(issue.month()+" · "+issue.reason()));
                b.evidence("ProfitStructureRepository.read",state,"마감 사본/유효 이관/미마감 공통 읽기 계산",month.sources());
            }
        }
        b.targets.add(target("COMPANY","ALL","회사",new ArrayList<>(selected.keySet())));
        b.table("회사 실적",cols,rows,"BASE");b.link("UI-08","같은 기간의 원래 손익표");
    }
    private void companyTargets(Builder b) {
        var target=target("COMPANY","ALL","회사 목표 대비",List.of("salesTargetAmt","salesActualAmt","operatingProfitTargetAmt","operatingProfitActualAmt"));b.authorize(target);b.targets.add(target);
        List<Row> rows=new ArrayList<>();
        for(int year=b.i.from().getYear();year<=b.i.to().getYear();year++) {
            var end=year==b.i.to().getYear()?b.i.to():YearMonth.of(year,12);
            var data=performance.getBusinessPlanPerformance(new BusinessPlanPerformanceDtos.Query(year,end.toString()));
            for(var month:data.months())if(inRange(month.month(),b.i)) {
                b.provisional|=month.actual().provisional();
                rows.add(row(month.month(),target,"month",month.month(),"salesTarget",month.salesTargetAmt(),"salesActual",month.salesActualAmt(),"salesRate",month.salesAchievementRate(),"profitTarget",month.operatingProfitTargetAmt(),"profitActual",month.operatingProfitActualAmt(),"marginDifference",month.operatingMarginDifference()));
            }
            b.unknown.addAll(data.notices());b.evidence("BusinessPlanPerformanceService.getBusinessPlanPerformance",b.provisional?"PROVISIONAL":"CLOSED","UI-18 공통 목표/실적 · 목표 미등록은 NULL",data);
        }
        b.table("회사 목표 대비 실적",List.of(col("month","월","MONTH",""),col("salesTarget","매출 목표","DECIMAL","원"),col("salesActual","매출 실적","DECIMAL","원"),col("salesRate","매출 달성률","DECIMAL","%"),col("profitTarget","영업이익 목표","DECIMAL","원"),col("profitActual","영업이익 실적","DECIMAL","원"),col("marginDifference","목표 대비 이익률 차이","DECIMAL","%p")),rows,"BASE");b.link("UI-18","동일 기간 목표 대비 실적");
    }
    private void personal(Builder b) {
        boolean loss=b.question.question().contains("적자");
        var matches=resolve(b,mapper.people(),b.question.context().developerId(),"DEVELOPER");
        if(!loss&&matches.size()==1)b.authorize(target("DEVELOPER",matches.get(0).id(),matches.get(0).name(),List.of("revenue","totalCost","profit")));
        if(!loss&&matches.size()!=1){b.unknown.add("관리번호 또는 개발자 이름을 지정해 주세요.");return;}
        List<Row> rows=new ArrayList<>();
        for(int year=b.i.from().getYear();year<=b.i.to().getYear();year++) {
            var query=new IndividualProfitabilityInput.Query(year,"CUMULATIVE",true,null,null,null,null,null,"ALL",1,50,"name","ASC");
            var snapshot=individuals.snapshot(query);
            for(var person:individuals.project(snapshot,query,true).items()) {
                if(!loss&&!person.personId().equals(matches.get(0).id()))continue;
                for(var month:individuals.detailMonths(snapshot,person.personId(),true))if(inRange(api(month.month()),b.i)) {
                    if(snapshot.sources().states().stream().anyMatch(s->s.personId().equals(person.personId())&&s.fullMonthLeave(month.month())))continue;
                    var values=costs.summarize(List.of(month),true);
                    if(loss&&!values.confirmedLoss())continue;
                    b.provisional|=!snapshot.sources().protectedPersonMonth(month.month(),person.personId());
                    var target=target("DEVELOPER",person.personId(),person.name()+" · "+person.managementNo(),List.of("revenue","totalCost","profit"));b.targets.add(target);
                    rows.add(row(month.month()+person.personId(),target,"month",api(month.month()),"name",target.displayName(),"revenue",values.revenue(),"cost",values.totalCost(),"profit",values.profit()));
                    month.completeness().issues().forEach(i->b.unknown.add(i.message()));
                }
            }
            b.evidence("IndividualProfitabilityService.detailMonths / MonthlyCostService.summarize","PROVISIONAL","휴직 제외 · "+(loss?"영업이익 < 0 · ":"")+"원천 미확인은 NULL",snapshot.common());
        }
        b.table(loss?"적자 인력":"개인 원가·수익",List.of(col("month","기간","MONTH",""),col("name","인력 · 관리번호","TEXT",""),col("revenue","매출","DECIMAL","원"),col("cost","원가","DECIMAL","원"),col("profit","영업이익","DECIMAL","원")),rows,"BASE");
        b.link("UI-03","개인 원가·수익표 · 동일 기간 확인");
    }
    private void project(Builder b) {
        var found=resolve(b,mapper.projects(),b.question.context().projectId(),"PROJECT");
        if(found.size()!=1){b.unknown.add("프로젝트 ID 또는 유일한 프로젝트 이름을 지정해 주세요.");return;}
        var entity=found.get(0);b.authorize(target("PROJECT",entity.id(),entity.name(),List.of("cumulativeCost","forecastMargin","targetGap")));
        var detail=projects.getProjectCostAnalysis(entity.id(),b.i.to().toString());var p=detail.row();
        var target=target("PROJECT",entity.id(),entity.name(),List.of("cumulativeCost","forecastMargin","targetGap"));b.targets.add(target);
        var forecast=detail.forecast();
        // The display row normalizes PRE to ING; eligibility must retain the source state.
        if(b.i.function()==TURNKEY&&(!"TK".equals(p.contractType())||!"ING".equals(detail.sourceStatus())||!"ING".equals(p.status())||p.contractAmount()==null||new BigDecimal(p.contractAmount()).signum()<=0||forecast==null)) {b.unknown.add("진행 중이며 계약금액이 0원보다 큰 턴키 프로젝트만 예측할 수 있습니다. 계약 전·종료·다른 계약 유형은 대상을 다시 지정해 주세요.");return;}
        var rows=List.of(row(entity.id(),target,"name",entity.name(),"contract",p.contractAmount(),"cost",p.cumulativeCost(),"remaining",forecast!=null&&forecast.complete()?forecast.knownRemainingCost():null,"margin",p.forecastMargin(),"targetGap",p.targetGap(),"costProgress",p.budgetUsage(),"weeklyProgress",projects.getProjectCostWeeklyProgress(entity.id(),b.i.to().toString())));
        detail.completeness().issues().forEach(i->b.unknown.add(i.message()));
        if(forecast!=null)forecast.issues().forEach(i->b.unknown.add(i.message()));
        else b.unknown.add("턴키 전용 잔여원가·최종마진 예측은 이 프로젝트에 적용되지 않습니다. 조회 가능한 실제 원가는 공통 조회값으로 제공합니다.");
        b.provisional=!mapper.closed(b.i.to().toString().replace("-",""))||b.i.prediction();
        b.table("프로젝트 원가·예상 최종 마진",List.of(col("name","프로젝트","TEXT",""),col("contract","계약금액","DECIMAL","원"),col("cost","누적 실제원가","DECIMAL","원"),col("remaining","남은 예상원가","DECIMAL","원"),col("margin","예상 최종 마진율","DECIMAL","%"),col("targetGap","목표 대비","DECIMAL","%p"),col("costProgress","원가 진행률 (예산 사용률)","DECIMAL","%"),col("weeklyProgress","주간 메모 진척률","DECIMAL","%")),rows,"BASE");
        b.evidence("ProjectCostService.getProjectCostAnalysis",b.provisional?"PROVISIONAL":"CLOSED","09 실제 남은 MM과 공통 단가 우선순위",detail);b.link("UI-04","동일 프로젝트·기준월");
    }
    private void revenue(Builder b) {
        var result=revenue.forecast();List<Column> columns=List.of(col("month","월","MONTH",""),col("revenue","예상 매출","DECIMAL","원"));
        for(String scenario:List.of("LOW","BASE","HIGH")) {
            var rows=result.revenueMonths().stream().filter(m->inRange(m.month(),b.i)).map(m->row(m.month(),null,"month",m.month(),"revenue",scenario.equals("LOW")?m.projectRevenue():scenario.equals("HIGH")?m.allWonRevenue():m.totalRevenue())).toList();
            b.table("6개월 예상 매출",columns,rows,scenario);
        }
        if(b.question.question().contains("영업이익")) {
            var profits=profitForecast.forecast();
            var rows=profits.stream().filter(m->inRange(m.month(),b.i)).map(m->row(m.month(),null,"month",m.month(),"projectRevenue",m.projectRevenue(),"salesCost",m.salesCost(),"sga",m.sga(),"adjustment",m.previousAdjustment(),"profit",m.operatingProfit())).toList();
            b.table("예상 영업이익 (진행 프로젝트만)",List.of(col("month","월","MONTH",""),col("projectRevenue","진행 프로젝트 매출","DECIMAL","원"),col("salesCost","매출원가","DECIMAL","원"),col("sga","판관비 합계","DECIMAL","원"),col("adjustment","전월조정","DECIMAL","원"),col("profit","영업이익","DECIMAL","원")),rows,"BASE");
            profits.forEach(p->b.unknown.addAll(p.reasons()));b.evidence("OperatingProfitForecastService.forecast","PLANNED","진행 프로젝트 예상 매출 − 매출원가 − 판관비 합계 + 전월조정 · 미래 급여행 생성 없음",profits);
            b.assumptions.add("영업이익은 진행 프로젝트 매출만 사용한 기본 1개입니다. 위 기회 포함 기본 매출과 다르며 기회 원가를 지어내지 않습니다.");
        }
        b.assumptions.add("낮음=기회 수주 0%, 기본=저장 수주확률, 높음=100%. 진행 프로젝트 매출은 동일하며 철수 뒤 연장하지 않습니다.");
        result.revenueSources().stream().filter(s->s.amount()==null).forEach(s->b.unknown.add(s.name()+" · "+s.month()+" · "+s.basis()));
        b.provisional=true;b.evidence("SalesRevenueForecastService.forecast","PLANNED","12 월별 공통 매출예측 · 턴키 미청구 공급가 잔액 배분",result);b.link("UI-29","실적이 아닌 예측 기간의 원천 화면");
    }
    private void workforce(Builder b) {
        var source=forecastSources.load(YearMonth.from(b.at));
        int count=Math.toIntExact(java.time.temporal.ChronoUnit.MONTHS.between(b.i.from(),b.i.to())+1);
        for(String scenario:List.of("LOW","BASE","HIGH")) {
            var opportunities=source.opportunities().stream().map(o->new SalesForecastSources.Opportunity(o.id(),o.name(),o.type(),o.status(),o.startMonth(),o.months(),o.amount(),scenario.equals("LOW")?BigDecimal.ZERO:scenario.equals("HIGH")?new BigDecimal("100"):o.probability(),o.projectId())).toList();
            var variant=new SalesForecastSources.Snapshot(source.projects(),opportunities,source.needs(),source.plans(),source.people(),source.leaves(),source.candidates(),source.standards(),source.jobs(),source.assignments());
            var result=workforce.calculate(variant,b.at.toLocalDate(),b.i.from(),count,true);
            List<Row> rows=result.jobDiagnostics().stream().map(j->{var m=j.month();return row(j.jobCode()+m.month(),null,"month",m.month(),"job",j.jobCode(),"supply",m.supply(),"project",m.unfilledDemand(),"opportunity",m.opportunityDemand(),"demand",m.demand(),"difference",m.difference());}).toList();
            b.table("직무·월별 인력 과부족",List.of(col("month","월","MONTH",""),col("job","직무","TEXT",""),col("supply","가용 공급","DECIMAL","명"),col("project","진행 미충원 수요","DECIMAL","명"),col("opportunity","기회 수요","DECIMAL","명"),col("demand","필요 수요","DECIMAL","명"),col("difference","차이 (음수=부족)","DECIMAL","명")),rows,scenario);
            result.issues().forEach(i->b.unknown.add(i.reason()));
        }
        b.provisional=true;b.assumptions.add("기회 수주량 낮음 0% / 기본 저장확률 / 높음 100%. 과부족을 특정 개인의 확정 대기로 해석하지 마세요.");
        b.evidence("SalesWorkforceProjectionService.calculate","PLANNED","12 공통 수급 · 확정 MM/휴직/퇴사/연장/진행 미충원",source);b.link("UI-17","예측 기간의 공통 수요·공급");
    }
    private void cash(Builder b) {
        YearMonth from=b.i.from();
        if(b.i.function()==CASH_FORECAST){
            YearMonth current=YearMonth.from(b.at);from=current;String closed=mapper.latestClose();if(closed!=null){YearMonth next=YearMonth.parse(api(closed)).plusMonths(1);if(next.isAfter(from))from=next;}
            boolean defaultRange=b.i.from().equals(current)&&b.i.to().equals(current.plusMonths(2));
            if(!defaultRange&&(!b.i.from().equals(from)||!b.i.to().equals(from.plusMonths(2)))){b.unknown.add("자금 전망은 현재월과 마지막 마감 이월을 반영한 "+from+" ~ "+from.plusMonths(2)+"의 3개월만 지원합니다. 기간을 다시 지정해 주세요.");return;}
            b.from=from.toString();b.to=from.plusMonths(2).toString();
        }
        var months=b.i.function()==CASH_FORECAST?cash.calculateContiguousRange(from,3):java.util.stream.IntStream.rangeClosed(from.getYear(),b.i.to().getYear()).mapToObj(year->cash.calculateCashFlow(new CashFlowDtos.Query(year,CashFlowDtos.Unit.WON))).flatMap(year->year.months().stream()).filter(m->inRange(m.month(),b.i)).toList();
        var criteria=cash.calculateCashFlow(new CashFlowDtos.Query(b.at.getYear(),CashFlowDtos.Unit.WON));
        b.assumptions.add("최소 운영자금 "+criteria.minimumCash()+"원 · 기준월 "+criteria.minimumCashMonth()+" · 공통 자금수지의 운영 기준입니다.");
        var rows=months.stream().map(m->row(m.month(),null,"month",m.month(),"opening",m.openingBalance(),"inflow",m.incomeTotal(),"outflow",m.expenseTotal(),"closing",m.closingBalance(),"state",m.status())).toList();
        b.provisional=months.stream().anyMatch(m->!m.status().equals("ACTUAL"));months.forEach(m->m.issues().forEach(i->b.unknown.add(i.message())));
        b.table("자금수지",List.of(col("month","월","MONTH",""),col("opening","기초","DECIMAL","원"),col("inflow","유입","DECIMAL","원"),col("outflow","유출","DECIMAL","원"),col("closing","기말 잔액","DECIMAL","원"),col("state","상태","TEXT","")),rows,"BASE");
        b.evidence(b.i.function()==CASH_FORECAST?"CashFlowService.calculateContiguousRange":"CashFlowService.calculateCashFlow",b.provisional?"PLANNED":"CLOSED","14/15 공통 이월·중복제거 · 미확인 기초는 뒤 월에도 전파",months);b.link("UI-21","같은 자금수지 원천 · 손익이 아닌 현금");
    }
    private void receivables(Builder b) {
        b.from=b.to=YearMonth.from(b.at).toString();b.provisional=true;
        var result=receivables.allReceivables();
        var rows=result.items().stream().filter(r->b.i.minimumOverdue()==null||r.dunning().elapsedDays()>=b.i.minimumOverdue()).map(r->{var t=target("PROJECT",r.projectId(),r.projectName(),List.of("unpaidAmount"));b.targets.add(t);return row(r.billId(),t,"bill",r.billId(),"project",r.projectName(),"balance",r.unpaidAmount(),"due",r.dueDate().toString(),"days",Integer.toString(r.dunning().elapsedDays()),"step",Integer.toString(r.dunning().step()),"notice","등록 자료의 연체 지표 · 회수일 확인 필요");}).toList();
        b.table("미수 연체 위험 지표",List.of(col("bill","청구","TEXT",""),col("project","프로젝트","TEXT",""),col("balance","남은 잔액","DECIMAL","원"),col("due","납기","DATE",""),col("days","연체일","DECIMAL","일"),col("step","현재 단계","TEXT",""),col("notice","확인할 사항","TEXT","")),rows,"BASE");
        b.evidence("ReceivableService.allReceivables","PROVISIONAL","미취소·원청구·부분입금 후 양수 잔액 · 기준일 현재 연체 지표",result);b.link("UI-13","현재 미수 목록 · 자동 알림 발송 없음");
    }
    private void billing(Builder b) {
        List<Row> rows=new ArrayList<>();String projectId=null;
        if(b.question.context().projectId()!=null||b.question.question().contains("프로젝트")) {
            var found=resolve(b,mapper.projects(),b.question.context().projectId(),"PROJECT");if(found.size()!=1){b.unknown.add("청구를 볼 프로젝트 ID를 명확히 지정해 주세요.");return;}projectId=found.get(0).id();
            b.authorize(target("PROJECT",projectId,found.get(0).name(),List.of("billing")));
        }
        for(int year=b.i.from().getYear();year<=b.i.to().getYear();year++) {
            var data=bills.listAll(new BillDtos.Query(year,null,null,projectId,false,1,100,"id","ASC"));
            for(var bill:data)if(!bill.cancelled()&&inRange(bill.billingMonth(),b.i)) {
                var t=target("PROJECT",bill.projectId(),bill.projectName(),List.of("billing","receipts","unpaidAmount"));b.targets.add(t);
                rows.add(row(bill.id(),t,"bill",bill.id(),"project",bill.projectName(),"month",bill.billingMonth(),"supply",bill.supplyAmount(),"vat",bill.vatAmount(),"total",bill.totalAmount(),"receipt",bill.receiptAmount(),"balance",bill.unpaidAmount(),"due",bill.dueDate(),"days",Integer.toString(bill.dunning().elapsedDays()),"step",Integer.toString(bill.dunning().step())));
            }
            b.evidence("BillService.listAll","PROVISIONAL","06 원청구·취소 제외 · 청구월 범위, 입금/잔액/연체는 기준일 현재",data);
        }
        b.provisional=true;b.assumptions.add("청구월 범위의 유효 청구이며 입금·잔액·연체는 "+b.at.toLocalDate()+" 현재입니다. 과거 마감 당시 잔액을 복원하지 않습니다.");
        b.table("청구·입금·잔액",List.of(col("bill","청구","TEXT",""),col("project","프로젝트","TEXT",""),col("month","청구월","MONTH",""),col("supply","공급가","DECIMAL","원"),col("vat","부가세","DECIMAL","원"),col("total","청구합계","DECIMAL","원"),col("receipt","입금","DECIMAL","원"),col("balance","잔액","DECIMAL","원"),col("due","납기","DATE",""),col("days","연체일","DECIMAL","일"),col("step","현재 단계","TEXT","")),rows,"BASE");b.link("UI-13","동일 청구월 범위 · 기준일 현재 입금/잔액");
    }
    private void assignments(Builder b) {
        List<Row> rows=new ArrayList<>();
        for(int year=b.i.from().getYear();year<=b.i.to().getYear();year++) {
            var filter=new AssignmentActionsDtos.Filter(year,null,null,null,null,false);
            var data=assignments.listAll(new AssignmentInput.Query(filter,1,100,"projectName","ASC"));
            for(var a:data)if(!a.endDate().isBefore(b.i.from().atDay(1))&&!a.startDate().isAfter(b.i.to().atEndOfMonth())&&(!b.question.question().contains("현재")||!a.startDate().isAfter(b.at.toLocalDate())&&!a.endDate().isBefore(b.at.toLocalDate()))) {
                var t=target("DEVELOPER",a.developerId(),a.developerName()+" · "+a.managementNo(),List.of("assignments"));b.targets.add(t);b.targets.add(target("PROJECT",a.projectId(),a.projectName(),List.of("assignments")));
                rows.add(row(a.id()+year,t,"id",a.id(),"project",a.projectName(),"person",t.displayName(),"job",a.jobName(),"start",a.startDate().toString(),"end",a.endDate().toString(),"year",Integer.toString(year),"mm",a.yearMm(),"type",a.inputType()));
            }
            b.evidence("AssignmentService.listAll","PROVISIONAL","05 기존 투입 조회 · 취소 제외 · 실제 기간/철수/연장 · 가배정은 포함하지 않음",data);
        }
        b.provisional=true;b.table("투입 배정 현황",List.of(col("id","배정 ID","TEXT",""),col("project","프로젝트","TEXT",""),col("person","인력 · 관리번호","TEXT",""),col("job","직무","TEXT",""),col("start","투입일","DATE",""),col("end","철수일","DATE",""),col("year","조회 연도","TEXT",""),col("mm","해당 연도 MM","DECIMAL","MM"),col("type","투입 구분","TEXT","")),rows,"BASE");b.link("UI-12","같은 연도·투입 기간");
    }
    private void opportunities(Builder b) {
        List<Row> rows=new ArrayList<>();b.from=b.to=YearMonth.from(b.at).toString();
        for(int page=1;;page++) {
            var data=opportunities.list(new SalesOpportunityDtos.Query("ING",false,null,page,100,"id","ASC"));
            for(var o:data.items()) {
                var t=target("OPPORTUNITY",o.id(),o.name(),List.of("amount","probability"));b.targets.add(t);
                rows.add(row(o.id(),t,"id",o.id(),"name",o.name(),"stage",o.stage(),"start",o.startMonth(),"amount",o.amount(),"probability",o.probability(),"needs",Integer.toString(o.needs().size())));
            }
            b.evidence("SalesOpportunityService.list","PROVISIONAL","12 진행 기회·저장 수주확률 · 가배정은 확정 투입이 아님",data);
            if((long)page*100>=data.total())break;
        }
        b.provisional=true;b.table("진행 영업 기회 · 파이프라인",List.of(col("id","기회 ID","TEXT",""),col("name","기회","TEXT",""),col("stage","단계","TEXT",""),col("start","시작월","MONTH",""),col("amount","예상금액","DECIMAL","원"),col("probability","저장 수주확률","DECIMAL","%"),col("needs","필요행 수","DECIMAL","건")),rows,"BASE");b.link("UI-29","현재 진행 기회 원천");
    }
    private void settlement(Builder b) {
        var tabs=settlements.listEligibleParts(b.i.to().toString());List<Row> rows=new ArrayList<>();
        for(var part:tabs.parts())if(b.question.question().contains(part.partName())||b.question.question().contains(part.partId())) {
            var result=settlements.previewSettlement(part.partId(),b.i.to().toString());
            var t=target("PART",part.partId(),part.partName(),List.of("settlement"));b.targets.add(t);
            rows.add(row(part.partId(),t,"name",part.partName(),"state",result.status(),"profit",result.totals().partProfitAmount()));
            result.issues().forEach(i->b.unknown.add(i.message()));b.evidence("SalesPartSettlementService.previewSettlement","PROVISIONAL","기존 정산 조회 · 재정산하지 않음",result);
        }
        if(rows.isEmpty()){b.unknown.add("정산할 영업파트 이름 또는 ID와 월을 지정해 주세요.");return;}
        b.provisional=true;b.table("영업파트 정산",List.of(col("name","파트","TEXT",""),col("state","정산 상태","TEXT",""),col("profit","파트 이익금","DECIMAL","원")),rows,"BASE");b.link("UI-19","동일 파트·월");
    }
    private List<AnalysisAssistantMapper.Entity> resolve(Builder b,List<AnalysisAssistantMapper.Entity> all,String contextId,String type) {
        var numbered=all.stream().filter(e->mentionsNumber(b.question.question(),e.number())).toList();
        var explicit=numbered.isEmpty()?all.stream().filter(e->mentions(b.question.question(),e)).toList():numbered;
        boolean here=b.question.question().matches("(?s).*(이\\s*(개발자|프로젝트|사람|화면)|선택한\\s*(개발자|프로젝트)).*");
        var matches=explicit.isEmpty()&&here&&contextId!=null?all.stream().filter(e->e.id().equals(contextId)).toList():explicit;
        if(matches.size()>1)b.unknown.add("대상이 여러 개입니다. 번호로 다시 지정해 주세요: "+String.join(", ",matches.stream().map(e->e.name()+" ("+e.number()+")").toList()));
        return matches;
    }
    private static boolean mentionsNumber(String question,String number){return number!=null&&!number.isBlank()&&java.util.regex.Pattern.compile("(?<![A-Za-z0-9_-])"+java.util.regex.Pattern.quote(number)+"(?![A-Za-z0-9_-])").matcher(question).find();}
    private static boolean mentions(String question,AnalysisAssistantMapper.Entity entity){return mentionsNumber(question,entity.number())||entity.name()!=null&&!entity.name().isBlank()&&question.contains(entity.name());}
    static boolean inRange(String month,AnalysisQuestionInterpreter.Intent i){return month.compareTo(i.from().toString())>=0&&month.compareTo(i.to().toString())<=0;}
    static Column col(String key,String name,String type,String unit){return new Column(key,name,type,unit);}
    static Cell cell(String key,String value){return new Cell(key,value,value==null?"자료 미확인 · 0으로 보정하지 않습니다.":null);}
    static Target target(String type,String id,String name,List<String> metrics){return new Target(type,id,name,metrics);}
    static Row row(String key,Target target,String... values){List<Cell> cells=new ArrayList<>();for(int i=0;i<values.length;i+=2)cells.add(cell(values[i],values[i+1]));return new Row(key,target,List.copyOf(cells),null);}
    private final class Builder {
        final String user;final Question question;final AnalysisQuestionInterpreter.Intent i;final OffsetDateTime at;
        final List<Table> tables=new ArrayList<>();final List<Evidence> evidence=new ArrayList<>();final List<ScreenLink> links=new ArrayList<>();
        final List<Target> targets=new ArrayList<>();final List<String> unknown=new ArrayList<>(),assumptions=new ArrayList<>();
        boolean provisional=false;String from,to;
        Builder(String user,Question q,AnalysisQuestionInterpreter.Intent i,OffsetDateTime at){this.user=user;this.question=q;this.i=i;this.at=at;from=i.from().toString();to=i.to().toString();targets.add(target("FUNCTION",i.function().name(),i.function().name(),List.of(i.function().name())));}
        void authorize(Target t){policy.require(user,new Scope(List.of(t),List.of(),from,to,at.toLocalDate(),i.to().toString()));}
        Scope scope(){return new Scope(targets.stream().distinct().toList(),i.minimumOverdue()==null?List.of():List.of(new Filter("overdueDays","GE",List.of(i.minimumOverdue().toString()))),from,to,at.toLocalDate(),i.prediction()?YearMonth.from(at).toString():to);}
        void evidence(String name,String state,String basis,Object source){evidence.add(new Evidence(name,scope(),state,basis,List.of(revisions.revision(source))));}
        void link(String screen,String guidance){links.add(new ScreenLink(screen,scope(),guidance));}
        void table(String title,List<Column> columns,List<Row> rows,String scenario){
            List<Cell> totals=new ArrayList<>();for(var c:columns)if(c.valueType().equals("DECIMAL")&&!Set.of("%","%p","일").contains(c.unit())) {
                BigDecimal sum=BigDecimal.ZERO;boolean known=true;for(var r:rows){var value=r.cells().stream().filter(v->v.columnKey().equals(c.key())).findFirst().orElseThrow().value();if(value==null){known=false;break;}sum=sum.add(new BigDecimal(value));}totals.add(cell(c.key(),known?sum.toPlainString():null));
            }
            String period=Set.of(RECEIVABLE,OPPORTUNITIES).contains(i.function())?at.toLocalDate()+" 현재":from+" ~ "+to;
            tables.add(new Table("table-"+tables.size(),title,period,title,"원/명/% 지표별",i.prediction()?"PLANNED":provisional?"PROVISIONAL":"CLOSED",scenario,columns,rows,rows.size(),totals,List.of(new Sort(columns.get(0).key(),"ASC")),50,rows.isEmpty()?"정상 조회 0건":null));
        }
        Result noAnswer(String reason){return result("NO_ANS",reason,List.of());}
        boolean hasValue(String key){return tables.stream().flatMap(t->t.rows().stream()).flatMap(r->r.cells().stream()).anyMatch(c->c.columnKey().equals(key)&&c.value()!=null);}
        Result finish(){boolean known=tables.stream().anyMatch(t->t.rows().isEmpty()||t.rows().stream().anyMatch(r->r.cells().stream().anyMatch(c->c.value()!=null&&t.columns().stream().anyMatch(col->col.key().equals(c.columnKey())&&col.valueType().equals("DECIMAL")))));if(i.function()==TURNKEY)known=hasValue("margin");
            if(i.function()==REVENUE_FORECAST&&!question.question().contains("매출"))known=hasValue("profit");
            return result(known?"OK":"NO_ANS",known?null:unknown.isEmpty()?"자료 없음 또는 계산에 필요한 원천이 미확인입니다.":unknown.get(0),tables);}
        Result result(String status,String reason,List<Table> values){return new Result("1",status.equals("OK")?"등록 자료의 공통 조회 결과입니다. 기간·잠정 상태와 미확인 항목을 함께 확인하세요.":"답할 수 없음 · "+reason,values,List.copyOf(assumptions),unknown.stream().distinct().toList(),List.copyOf(evidence),List.of("원래 화면에서 대상·기간과 자료 상태를 확인해 주세요. 업무 변경은 해당 화면에서 직접 실행하세요."),question.context(),scope(),status,reason,List.copyOf(links),new Interpreter("SERVER_RULE",null,"서버 규칙으로 해석·외부 모델 미사용"),at);}
    }
}
