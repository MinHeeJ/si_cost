package com.sicost.apiops;

import com.sicost.profitability.*;
import com.sicost.profitability.MonthlyCostDtos.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.sicost.apiops.ProjectAnalysisDtos.*;

class ProjectCalculationTest {
    final ProjectCostCalculation costs=new ProjectCostCalculation(new TurnkeyRevenueCalculation());
    final ProjectStatusCalculation board=new ProjectStatusCalculation();
    final YearMonth selected=YearMonth.of(2026,1);
    static BigDecimal n(String v){return new BigDecimal(v);}
    Project project(String type){return new Project("project","프로젝트",type,"ING",null,LocalDate.of(2025,12,1),LocalDate.of(2026,12,31),n("1000"),n("800"),n("0"),n("0"));}
    MonthlyPersonCost person(String month,String person,String labor,String revenue) {
        var row=new StoredCost(month,person,"project","asgn-"+person,n("0.50"),n("0.00"),n(revenue),labor==null?null:n(labor),n("0"),n("0"),n("0"),"ALLOC","TK",null,"N",null,null,null,null,"프로젝트","DEV","PAID").toRow();
        var source=new MonthlyLaborSource(person,month,"FT","DRCT","급여",labor,null,null,null,null,null,null,null,null,false);
        return new MonthlyPersonCost(person,month,List.of(row),source,new MonthlyCostCompleteness(labor!=null,true,false,true,List.of()),labor,revenue,"0.50",false,false);
    }
    Snapshot snapshot(Project project,List<MonthlyPersonCost> people,List<ForecastMonth> future,List<Stage> stages,List<Memo> memos,List<Issue> issues) {
        var period=new ProfitabilityPeriod(2026,"202501","202501","202601",OffsetDateTime.parse("2026-01-31T00:00:00+09:00"),false);
        var sources=new Sources(period,"admin",LocalDate.of(2026,1,31),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(project),List.of(),List.of(),List.of(),
            List.of(new Standard("TGT_MRGN_RT","202501",n("20")),new Standard("LOW_MRGN_GAP","202501",n("5"))),List.of(),List.of(),List.of());
        return new Snapshot(sources,new MonthlyCostsData(period,people),List.of(new ProjectEvidence("project",n("1000"),n("0.00"),project.fromDate())),
            List.of(new Expense("project","202512",n("7"))),stages,memos,issues,List.of(),future,List.of(new StageTemplate("ONE","TK,IN,MO,DP")));
    }
    @Test void cumulativeIncludesDecemberAndJanuaryAndOneExtraExpenseNotAnnualReset() {
        var project=project("TK");var snapshot=snapshot(project,List.of(person("202512","actual","100","0"),person("202601","actual","100","0")),List.of(),List.of(),List.of(),List.of());
        var row=costs.row(snapshot,project,selected);
        assertThat(row.get("cumulativeCost")).isEqualTo("207");assertThat(row.get("actualMm")).isEqualTo("1.00");assertThat(row.get("cumulativeRevenue")).isEqualTo("258");
        assertThat(row.get("planMm")).isEqualTo("0.00");assertThat(row.get("cumulativeProfit")).isEqualTo("51");
        assertThat(((Completeness)row.get("completeness")).rangeStartMonth()).isEqualTo("2025-12");
    }
    @Test void latestCompletePersonMonthUsedOnceAcrossFutureAssignmentsAndFractionNotCutMidway() {
        var project=project("TK");var people=List.of(person("202512","actual","101","0"),person("202601","actual","103","0"));
        var future=List.of(new ForecastMonth("project","a","actual","2026-02",n("0.33")),new ForecastMonth("project","b","actual","2026-03",n("0.33")));
        var row=costs.row(snapshot(project,people,future,List.of(),List.of(),List.of()),project,selected);
        // C=211; F=DOWN((.33+.33)*103)=67, not 33+33. (1000-211-67)/1000=72.2%.
        assertThat(row.get("forecastMargin")).isEqualTo("72.2");
    }
    @Test void explicitZeroLatestLaborIsValidNotMissingUnitCost() {
        var project=project("TK");var future=List.of(new ForecastMonth("project","a","actual","2026-02",n("1.00")));
        var row=costs.row(snapshot(project,List.of(person("202601","actual","0","0")),future,List.of(),List.of(),List.of()),project,selected);
        assertThat(row.get("forecastMargin")).isEqualTo("99.3");
        assertThat(((Completeness)row.get("completeness")).issues()).isEmpty();
    }
    @Test void unpricedPersonNullsFinalForecastWhileKeepingKnownCostAndReason() {
        var project=project("TK");var future=List.of(new ForecastMonth("project","a","missing-person","2026-02",n("1.00")));
        var row=costs.row(snapshot(project,List.of(person("202601","actual","100","0")),future,List.of(),List.of(),List.of()),project,selected);
        assertThat(row.get("forecastMargin")).isNull();assertThat(row.get("cumulativeCost")).isEqualTo("107");
        assertThat(((Completeness)row.get("completeness")).issues()).anyMatch(i->"missing-person".equals(i.personId())&&"forecastMargin".equals(i.metric()));
    }
    @Test void signalsRoundBeforeThresholdAndMemoNullRetainsPreviousActualAndDateUsesLatestWriting() {
        var project=new Project("project","프로젝트","TK","ING",null,LocalDate.of(2026,1,12),LocalDate.of(2026,4,21),n("1000"),n("800"),n("0"),n("0"));
        var snapshot=snapshot(project,List.of(person("202601","actual","199","0")),List.of(),List.of(),
            List.of(new Memo("project",1,LocalDate.of(2026,1,30),n("10")),new Memo("project",2,LocalDate.of(2026,1,31),null)),List.of());
        var row=board.row(snapshot,project,costs.row(snapshot,project,selected),LocalDate.of(2026,1,31));
        assertThat(row.get("planProgress")).isEqualTo("19.2");assertThat(row.get("actualProgress")).isEqualTo("10.0");assertThat(row.get("light")).isEqualTo("주의");
        assertThat(row.get("lastUpdatedDate")).isEqualTo(LocalDate.of(2026,1,31));assertThat(row.get("elapsedDays")).isEqualTo(0);
    }
    @Test void endedTurnkeyRevenueUsesContractRatherThanBudgetProgressAndKeepsMissingCostUnknown() {
        var project=new Project("project","프로젝트","TK","ING",null,LocalDate.of(2025,12,1),LocalDate.of(2026,1,15),n("1000"),n("800"),n("0"),n("0"));
        for(String labor:Arrays.asList("100",null)) {
            var row=costs.row(snapshot(project,List.of(person("202601","actual",labor,"0")),List.of(),List.of(),List.of(),List.of()),project,selected);
            assertThat(row.get("status")).isEqualTo("END");
            assertThat(row.get("cumulativeRevenue")).isEqualTo("1000");
            assertThat(row.get("forecastMargin")).isNull();
            if(labor==null) {
                assertThat(row.get("cumulativeCost")).isNull();
                assertThat(row.get("cumulativeMargin")).isNull();
            }
        }
    }
    @Test void boardAndProgressDetailUseTheSameDateProportionIncludingBoundaryDays() {
        var project=project("TK");var snapshot=snapshot(project,List.of(),List.of(),List.of(),List.of(),List.of());
        var detail=new com.sicost.progress.ProjectProgressDtos.ProjectRow(project.projectId(),project.name(),project.contractType(),project.status(),project.fromDate(),project.toDate(),project.budget(),project.fromDate().atStartOfDay(),null,null,false);
        for(var day:List.of(project.fromDate(),project.fromDate().plusDays(1),LocalDate.of(2026,1,31),project.toDate())) {
            var row=board.row(snapshot,project,costs.row(snapshot,project,selected),day);
            assertThat(row.get("planProgress")).isEqualTo(com.sicost.progress.ProjectProgressCalculation.plan(detail,List.of(),day));
        }
    }
    @Test void dueTodayNotOverdueAdditionalStatusRemainsOpenAndFourteenDaysNotStale() {
        var project=project("MO");var snapshot=snapshot(project,List.of(),List.of(),List.of(),List.of(new Memo("project",1,LocalDate.of(2026,1,17),null)),
            List.of(new Issue("project","HIGH",LocalDate.of(2026,1,31),"CUSTOM"),new Issue("project","HIGH",LocalDate.of(2026,1,1),"CLOSE")));
        var row=board.row(snapshot,project,costs.row(snapshot,project,selected),LocalDate.of(2026,1,31));
        assertThat(row.get("openIssueCount")).isEqualTo(1);assertThat(row.get("overdueIssueCount")).isEqualTo(0);assertThat(row.get("updateWarning")).isEqualTo(false);assertThat(row.get("light")).isEqualTo("정상");
    }
    @Test void validStagesWeightedPlanAndRawCompletedDatesRetainedWhileInvalidUsesPeriodFallback() {
        var project=project("TK");var stage=new Stage("project",1,"ONE","전체",n("100.00"),project.fromDate(),project.toDate(),null,"admin","관리자",project.fromDate().atStartOfDay(),null,null,null);
        var snapshot=snapshot(project,List.of(),List.of(),List.of(stage),List.of(),List.of());
        var row=board.row(snapshot,project,costs.row(snapshot,project,selected),LocalDate.of(2026,1,31));assertThat(row.get("planBasis")).isEqualTo("단계 기준");
        var bad=new Stage("project",1,"ONE","부분",n("30.00"),project.fromDate(),project.toDate(),null,"admin","관리자",project.fromDate().atStartOfDay(),null,null,null);
        var invalid=snapshot(project,List.of(),List.of(),List.of(bad),List.of(),List.of());
        var fallback=board.row(invalid,project,costs.row(invalid,project,selected),LocalDate.of(2026,1,31));assertThat(fallback.get("planProgress")).isEqualTo(row.get("planProgress"));assertThat(fallback.get("planBasis").toString()).contains("불완전");assertThat(invalid.stages()).containsExactly(bad);
    }
}
