package com.sicost.apiops;

import com.sicost.profitability.MonthlyCostDtos.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.sicost.apiops.ProjectAnalysisDtos.*;

class ProjectCalculationRegressionTest {
    final ProjectCalculationTest fixture=new ProjectCalculationTest();
    @Test void pastFreelancerWithoutSelectedMonthAssignmentCannotSupplyFutureUnitCost() {
        var p=fixture.project("TK");var person=fixture.person("202512","fl","100","0");
        var labor=new MonthlyLaborSource("fl","202512","FL","DRCT","급여","100",null,null,null,null,null,null,null,null,false);
        var freelancer=new MonthlyPersonCost(person.personId(),person.month(),person.rows(),labor,person.completeness(),person.knownLaborAllocated(),person.knownPersonalRevenue(),person.assignmentMm(),false,false);
        var s=fixture.snapshot(p,List.of(freelancer),List.of(new ForecastMonth("project","future","fl","2026-02",BigDecimal.ONE)),List.of(),List.of(),List.of());
        var row=fixture.costs.row(s,p,fixture.selected);
        assertThat(row.get("forecastMargin")).isNull();
        assertThat(((Completeness)row.get("completeness")).issues()).anyMatch(i->"fl".equals(i.personId())&&"forecastMargin".equals(i.metric()));
    }
    @Test void distributionFallbackUsesOnlyMonthsWithRemainingAssignmentMm() {
        var p=fixture.project("TK");var original=fixture.snapshot(p,List.of(),List.of(new ForecastMonth("project","future","unpaid","2026-02",BigDecimal.ONE)),List.of(),List.of(),List.of());
        var x=original.sources();
        var cells=List.of(new TurnkeyCell("project","PLAN","1","202602","DEV","unpaid",null,new BigDecimal("100")),new TurnkeyCell("project","PLAN","1","202603","DEV","unpaid",null,new BigDecimal("1000")));
        var sources=new Sources(x.period(),x.actor(),x.asOfDate(),x.people(),x.prices(),x.states(),x.payroll(),x.assignments(),x.projectMonths(),x.projects(),x.projectParts(),cells,x.storedCosts(),x.standards(),x.parts(),x.closes(),x.allowanceCodes());
        var snapshot=new Snapshot(sources,original.costs(),original.evidence(),original.expenses(),original.stages(),original.memos(),original.issues(),original.managers(),original.future(),original.templates());
        // Cost 7; February contribution DOWN(100*800/1000)=80. No March assignment.
        assertThat(fixture.costs.row(snapshot,p,fixture.selected).get("forecastMargin")).isEqualTo("91.3");
    }
    @Test void nullBudgetFallbackUsesTheExistingIntegerAutoBudgetButManualZeroStaysUnknown() {
        for (BigDecimal budget: Arrays.asList(null,BigDecimal.ZERO)) {
            var old=fixture.project("TK");var p=new Project(old.projectId(),old.name(),old.contractType(),old.status(),null,old.fromDate(),old.toDate(),new BigDecimal("1001"),budget,old.teamExpense(),old.projectExpense());
            var original=fixture.snapshot(p,List.of(),List.of(new ForecastMonth("project","future","unpaid","2026-02",BigDecimal.ONE)),List.of(),List.of(),List.of());var x=original.sources();
            var cells=List.of(new TurnkeyCell("project","PLAN","1","202602","DEV","unpaid",null,new BigDecimal("5000")));
            var sources=new Sources(x.period(),x.actor(),x.asOfDate(),x.people(),x.prices(),x.states(),x.payroll(),x.assignments(),x.projectMonths(),x.projects(),x.projectParts(),cells,x.storedCosts(),List.of(new Standard("TGT_MRGN_RT","202501",new BigDecimal("20.04"))),x.parts(),x.closes(),x.allowanceCodes());
            var snapshot=new Snapshot(sources,original.costs(),original.evidence(),original.expenses(),original.stages(),original.memos(),original.issues(),original.managers(),original.future(),original.templates());
            var margin=fixture.costs.row(snapshot,p,fixture.selected).get("forecastMargin");
            // B=DOWN(1001 * .7996)=800; F=DOWN(5000*800/1001)=3996, with C=7.
            if(budget==null)assertThat(margin).isEqualTo("-299.9");else assertThat(margin).isNull();
        }
    }
    @Test void listPrimaryCauseUsesTheSameLargestCauseAsTheDetailCalculator() {
        var p=fixture.project("MO");var original=fixture.snapshot(p,List.of(fixture.person("202601","worker","900","100")),List.of(),List.of(),List.of(),List.of());var x=original.sources();
        var standards=new ArrayList<>(x.standards());standards.add(new Standard("COST_OVER_UPRC_RT","202501",new BigDecimal("40")));
        var sources=new Sources(x.period(),x.actor(),x.asOfDate(),x.people(),x.prices(),x.states(),x.payroll(),x.assignments(),x.projectMonths(),x.projects(),x.projectParts(),x.turnkey(),x.storedCosts(),standards,x.parts(),x.closes(),x.allowanceCodes());
        var snapshot=new Snapshot(sources,original.costs(),original.evidence(),original.expenses(),original.stages(),original.memos(),original.issues(),original.managers(),original.future(),original.templates());
        var row=fixture.costs.row(snapshot,p,fixture.selected);
        assertThat(row.get("alertCount")).isEqualTo(2);assertThat(row.get("primaryCause")).isEqualTo("투입이 계획보다 많음");
    }
    @Test void inactiveOrAdditionalLiveTemplateDoesNotChangeStoredStageProgress() {
        var p=fixture.project("TK");var stages=fixture.stages(p);
        var s=fixture.snapshot(p,List.of(),List.of(),stages,List.of(),List.of());
        var missingTemplate=new Snapshot(s.sources(),s.costs(),s.evidence(),s.expenses(),s.stages(),s.memos(),s.issues(),s.managers(),s.future(),List.of());
        var base=fixture.board.row(s,p,fixture.costs.row(s,p,fixture.selected),LocalDate.of(2026,1,31));
        var after=fixture.board.row(missingTemplate,p,fixture.costs.row(missingTemplate,p,fixture.selected),LocalDate.of(2026,1,31));
        assertThat(after.get("planBasis")).isEqualTo("단계 기준");assertThat(after.get("planProgress")).isEqualTo(base.get("planProgress"));
    }
}
