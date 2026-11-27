package com.sicost.apiops;

import com.sicost.profitability.*;
import com.sicost.profitability.MonthlyCostDtos.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.sicost.apiops.ProjectAnalysisDtos.*;
import static org.mockito.Mockito.*;

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
    com.sicost.progress.ProjectProgressDtos.ProjectRow progressProject(Project p) {
        return new com.sicost.progress.ProjectProgressDtos.ProjectRow(p.projectId(),p.name(),p.contractType(),p.status(),p.fromDate(),p.toDate(),p.budget(),p.fromDate().atStartOfDay(),null,null,false);
    }
    List<Stage> stages(Project p) {
        var codes=com.sicost.progress.ProjectProgressCalculation.codes(p.contractType());
        var weights=Set.of("TK","IN").contains(p.contractType())?List.of("5","15","20","35","15","5","5"):List.of("5","10","15","65","5");
        var templates=new ArrayList<com.sicost.progress.ProjectProgressDtos.Template>();
        for(int i=0;i<codes.size();i++)templates.add(new com.sicost.progress.ProjectProgressDtos.Template(codes.get(i),codes.get(i),weights.get(i),p.contractType(),"Y","N",i));
        return com.sicost.progress.ProjectProgressCalculation.initialize(progressProject(p),templates).stream().map(r->new Stage(r.projectId(),r.stepSn(),r.stepCode(),r.stepName(),r.weight(),r.startDate(),r.endDate(),r.completedDate(),"admin","관리자",p.fromDate().atStartOfDay(),null,null,null)).toList();
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
        var project=project("TK");var future=List.of(
            new ForecastMonth("project","a","missing-person","2026-02",n("1.00")),
            new ForecastMonth("project","b","missing-person","2026-03",n("2.00")),
            new ForecastMonth("project","c","actual","2026-02",n("4.00")));
        var row=costs.row(snapshot(project,List.of(person("202601","actual","100","0")),future,List.of(),List.of(),List.of()),project,selected);
        assertThat(row.get("forecastMargin")).isNull();assertThat(row.get("cumulativeCost")).isEqualTo("107");
        assertThat(row.get("targetGap")).isNull();
        assertThat(row.get("forecastEvidence")).isEqualTo(new ForecastEvidence(2,"7.00","400",1,List.of("missing-person")));
        assertThat(((Completeness)row.get("completeness")).issues()).anyMatch(i->"UNIT_COST".equals(i.code())&&"missing-person".equals(i.personId())&&"forecastMargin".equals(i.metric()));
    }
    @Test void signalsRoundBeforeThresholdAndMemoNullRetainsPreviousActualAndDateUsesLatestWriting() {
        var project=new Project("project","프로젝트","TK","ING",null,LocalDate.of(2026,1,12),LocalDate.of(2026,4,21),n("1000"),n("800"),n("0"),n("0"));
        var snapshot=snapshot(project,List.of(person("202601","actual","199","0")),List.of(),List.of(),
            List.of(new Memo("project",1,LocalDate.of(2026,1,30),n("10")),new Memo("project",2,LocalDate.of(2026,1,31),null)),List.of());
        var row=board.row(snapshot,project,costs.row(snapshot,project,selected),LocalDate.of(2026,1,31));
        assertThat(row.get("planProgress")).isEqualTo("19.0");assertThat(row.get("actualProgress")).isEqualTo("10.0");assertThat(row.get("light")).isEqualTo("주의");
        assertThat(row.get("lastUpdatedDate")).isEqualTo(LocalDate.of(2026,1,31));assertThat(row.get("elapsedDays")).isEqualTo(0);
    }
    @Test void boardAndDetailDistinguishAbsentMemoWithoutChangingFourteenDayBoundary() {
        var p=project("MO");
        var repository=mock(com.sicost.progress.ProjectProgressRepository.class);
        var costSource=mock(com.sicost.progress.ProjectProgressCostSource.class);
        var revision=mock(com.sicost.common.concurrent.ProjectProgressAggregateRevision.class);
        when(repository.required(anyString(),any())).thenReturn(progressProject(p));
        when(costSource.load(any(),any())).thenReturn(new com.sicost.progress.ProjectProgressDtos.Cost(
            BigDecimal.ZERO,new com.sicost.progress.ProjectProgressDtos.Completeness(true,true,false,List.of(),0,"2025-12","2026-01",null)));
        for(boolean absent:List.of(true,false)) {
            for(int elapsed:List.of(0,14,15,30)) {
                LocalDate day=p.fromDate().plusDays(elapsed);
                var memos=absent?List.<Memo>of():List.of(new Memo(p.projectId(),1,p.fromDate(),null));
                when(repository.memos(p.projectId())).thenReturn(absent?List.of():List.of(
                    new com.sicost.progress.ProjectProgressDtos.MemoRow(p.projectId(),1,p.fromDate(),null,null,null,null,
                        "admin","관리자",p.fromDate().atStartOfDay(),null,null,null)));
                var service=new com.sicost.progress.ProjectProgressQueryService(repository,revision,costSource,
                    mock(com.sicost.common.context.CurrentUserProvider.class),
                    Clock.fixed(day.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),ZoneId.of("Asia/Seoul")));
                var detail=service.getProjectProgress(p.projectId(),2025).status();
                var snapshot=snapshot(p,List.of(),List.of(),List.of(),memos,List.of());
                var row=board.row(snapshot,p,costs.row(snapshot,p,selected),day);
                String kind=absent?"주간 메모 없음":"메모경과";
                assertThat(row.get("elapsedDays")).isEqualTo(detail.elapsedDays()).isEqualTo(elapsed);
                assertThat(row.get("light")).isEqualTo(detail.light()).isEqualTo(elapsed>14?"주의":"정상");
                assertThat(detail.reasons()).filteredOn(r->r.kind().equals(kind)).hasSize(elapsed>14?1:0);
                @SuppressWarnings("unchecked")
                var reasons=(List<Map<String,Object>>)row.get("reasons");
                assertThat(reasons).filteredOn(r->kind.equals(r.get("kind"))).hasSize(elapsed>14?1:0);
                if(elapsed>14)assertThat(reasons.get(0).get("value")).isEqualTo(detail.reasons().get(0).value());
            }
        }
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
    @Test void inclusiveStageDaysAgreeForFixedNovemberSixteenthAndStageEdges() {
        LocalDate today=LocalDate.of(2026,11,16);
        // Isolated schedules: 5 completed + analysis weight 15 * elapsed / inclusive days.
        for(var example:List.of(
            new String[]{"턴키01","TK","2026-11-10","2026-12-06","8.3"},
            new String[]{"내부01","IN","2026-11-06","2026-11-25","12.5"},
            new String[]{"B9턴키ID","TK","2026-11-06","2026-11-18","16.5"})) {
            LocalDate analysisStart=LocalDate.parse(example[2]),analysisEnd=LocalDate.parse(example[3]);
            var p=new Project("project",example[0],example[1],"ING",null,LocalDate.of(2026,11,1),
                analysisEnd.plusDays(5),n("1000"),n("800"),n("0"),n("0"));
            var codes=com.sicost.progress.ProjectProgressCalculation.codes(p.contractType());
            var weights=List.of("5","15","20","35","15","5","5");
            var rows=new ArrayList<com.sicost.progress.ProjectProgressDtos.StepRow>();
            LocalDate start=p.fromDate();
            for(int i=0;i<7;i++) {
                LocalDate end=i==0?analysisStart.minusDays(1):i==1?analysisEnd:start;
                rows.add(new com.sicost.progress.ProjectProgressDtos.StepRow(p.projectId(),i+1,codes.get(i),codes.get(i),
                    n(weights.get(i)),start,end,null,"admin","관리자",start.atStartOfDay(),null,null,null));
                start=end.plusDays(1);
            }
            assertThat(com.sicost.progress.ProjectProgressCalculation.valid(progressProject(p),rows)).isTrue();
            var stages=rows.stream().map(r->new Stage(r.projectId(),r.stepSn(),r.stepCode(),r.stepName(),r.weight(),
                r.startDate(),r.endDate(),null,r.registeredBy(),r.registeredName(),r.registeredAt(),null,null,null)).toList();
            var snapshot=snapshot(p,List.of(),List.of(),stages,List.of(new Memo(p.projectId(),1,today,n("0"))),List.of());
            for(var day:List.of(analysisStart,today,analysisEnd)) {
                String expected=day.equals(analysisStart)?"5.0":day.equals(analysisEnd)?"20.0":example[4];
                assertThat(com.sicost.progress.ProjectProgressCalculation.plan(progressProject(p),rows,day))
                    .as(example[0]+" "+day).isEqualTo(expected);
                assertThat(board.row(snapshot,p,costs.row(snapshot,p,selected),day).get("planProgress"))
                    .as("board "+example[0]+" "+day).isEqualTo(expected);
            }
            for(var gap:List.of("4.9","5.0","9.9","10.0")) {
                var withActual=snapshot(p,List.of(),List.of(),stages,
                    List.of(new Memo(p.projectId(),1,analysisEnd,n("20.0").subtract(n(gap)))),List.of());
                var result=board.row(withActual,p,Map.of("cumulativeCost","0"),analysisEnd);
                assertThat(result.get("light")).isEqualTo(n(gap).compareTo(n("10"))>=0?"위험":n(gap).compareTo(n("5"))>=0?"주의":"정상");
            }
        }
    }
    @Test void validStagesWeightedPlanAndRawCompletedDatesRetainedWhileInvalidUsesPeriodFallback() {
        var project=project("TK");var stages=stages(project);
        var snapshot=snapshot(project,List.of(),List.of(),stages,List.of(),List.of());
        var row=board.row(snapshot,project,costs.row(snapshot,project,selected),LocalDate.of(2026,1,31));assertThat(row.get("planBasis")).isEqualTo("단계 기준");
        var first=stages.get(0);var bad=new Stage(first.projectId(),first.stepSn(),first.stepCode(),first.stepName(),n("30.00"),first.startDate(),first.endDate(),null,"admin","관리자",project.fromDate().atStartOfDay(),null,null,null);
        var invalidStages=new ArrayList<>(stages);invalidStages.set(0,bad);
        var invalid=snapshot(project,List.of(),List.of(),invalidStages,List.of(),List.of());
        var fallback=board.row(invalid,project,costs.row(invalid,project,selected),LocalDate.of(2026,1,31));
        assertThat(fallback.get("planProgress")).isEqualTo(com.sicost.progress.ProjectProgressCalculation.plan(progressProject(project),List.of(),LocalDate.of(2026,1,31)));
        assertThat(fallback.get("planBasis").toString()).contains("불완전");assertThat(invalid.stages()).containsExactlyElementsOf(invalidStages);
    }
}
