package com.sicost.profitability;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static com.sicost.profitability.MonthlyCostDtos.*;
import static com.sicost.profitability.MonthlyCostCalculationTest.*;
import static org.assertj.core.api.Assertions.*;

/** Pure independent boundary examples, not invented substitutes for runner-owned numeric OQ fixtures. */
class TurnkeyRevenueCalculationTest {
    final TurnkeyRevenueCalculation turnkey = new TurnkeyRevenueCalculation();
    final MonthlyCostCalculation costs = new MonthlyCostCalculation(new EstimatedPayrollAdapter());

    static Sources cells(Sources s, List<TurnkeyCell> cells) {
        return new Sources(s.period(),s.actor(),s.asOfDate(),s.people(),s.prices(),s.states(),s.payroll(),s.assignments(),s.projectMonths(),
            s.projects(),s.projectParts(),cells,s.storedCosts(),s.standards(),s.parts(),s.closes(),s.allowanceCodes());
    }
    static TurnkeyCell cell(String step, String month, String type, String person, String amount) {
        return new TurnkeyCell("project",step,"1",month,type,person,"ITEM".equals(type)?"기술료":null,n(amount));
    }
    static Sources tk(String budget, Assignment... assignments) {
        var p = project("TK","ACT");
        return sources(person("FL"),List.of(pay("FL","100")),List.of(assignments),
            List.of(new Project(p.projectId(),p.name(),p.contractType(),p.status(),p.loseDate(),p.fromDate(),p.toDate(),n("101"),n(budget),n("20"),n("10"))));
    }
    MonthlyPersonCost calculate(Sources s) { return turnkey.calculate(s,costs.calculate(s,s.people().get(0),MONTH)); }
    static Assignment free(String id, String mm) {
        var a=assignment(id,mm);
        return new Assignment(a.month(),a.personId(),a.projectId(),a.assignmentId(),a.assignmentMm(),a.appliedUnitPrice(),a.salesAmount(),a.fixed(),
            a.jobCode(),"FREE",a.contractPersonId(),a.status(),a.fromDate(),a.toDate());
    }

    @Test void tableIsChosenAcrossAllMonthsAndItemRowsNeverBecomePersonalRevenue() {
        var s=cells(tk("0",assignment("a","0.50")),List.of(
            cell("EST",MONTH,"DEV","actual","999"),cell("CTRT",MONTH,"DEV","actual","500"),
            cell("PLAN","202602","DEV","actual","100"),cell("PLAN",MONTH,"ITEM",null,"700")));
        var result=calculate(s);
        assertThat(turnkey.selected(s,"project")).extracting(c->c.step()).containsOnly("PLAN");
        assertThat(result.rows()).singleElement().satisfies(r->assertThat(r.personalRevenue()).isEqualTo("0"));
        assertThat(result.completeness().revenueComplete()).isTrue();
        assertThat(result.completeness().issues()).anyMatch(i->i.message().contains("NO_DISTRIBUTION"));
    }
    @Test void selectedAmountIsAllocatedOnceWithSortedLastResidualAndFreeZero() {
        var s=cells(tk("0",assignment("b","0.50"),free("free","1.00"),assignment("a","1.00")),
            List.of(cell("PLAN",MONTH,"DEV","actual","101")));
        var result=calculate(s);
        assertThat(result.rows()).extracting(MonthlyCostRow::assignmentId).containsExactly("a","b","free");
        assertThat(result.rows()).extracting(MonthlyCostRow::personalRevenue).containsExactly("67","34","0");
        assertThat(result.knownPersonalRevenue()).isEqualTo("101");
        assertThat(result.rows().get(2).laborAllocated()).isNotEqualTo("0");
        assertThat(s.turnkey().get(0).amount()).isEqualByComparingTo("101");
    }
    @Test void signedAmountsUseDownTowardZeroAndLastResidual() {
        var result=calculate(cells(tk("100",assignment("b","0.50"),assignment("a","1.00")),
            List.of(cell("PLAN",MONTH,"DEV","actual","-101"))));
        assertThat(result.rows()).extracting(MonthlyCostRow::personalRevenue).containsExactly("-67","-34");
    }
    @Test void repeatedPersonCellsAreSummedAndExplicitZeroDoesNotTriggerFallback() {
        var result=calculate(cells(tk("100",assignment("a","1.00")),List.of(
            cell("PLAN",MONTH,"DEV","actual","101"),
            new TurnkeyCell("project","PLAN","2",MONTH,"DEV","actual",null,n("-101")))));
        assertThat(result.knownPersonalRevenue()).isEqualTo("0");
        assertThat(result.completeness().revenueComplete()).isTrue();
    }
    @Test void noPaidMmDiagnosesUnallocatedTableAmountAndAddsNoRevenue() {
        var result=calculate(cells(tk("100",free("free","1.00")),List.of(cell("PLAN",MONTH,"DEV","actual","101"))));
        assertThat(result.knownPersonalRevenue()).isEqualTo("0");
        assertThat(result.completeness().allocationComplete()).isFalse();
        assertThat(result.completeness().issues()).anySatisfy(i->{
            assertThat(i.personId()).isEqualTo("actual");assertThat(i.month()).isEqualTo(MONTH);
            assertThat(i.source()).isEqualTo("TB_PJ_TK_DSTRB");assertThat(i.relatedScreen()).isEqualTo("UI-01");
            assertThat(i.sourceAmount()).isEqualTo("101");
        });
    }
    @Test void absentActualPersonUsesOnlyLaborAndStoredBudgetNotExpensesOrContractPersonTable() {
        var s=cells(tk("60",assignment("a","1.00")),List.of(cell("PLAN",MONTH,"DEV","contract-person","999")));
        var result=calculate(s);
        assertThat(result.rows()).singleElement().satisfies(r->{
            assertThat(r.personId()).isEqualTo("actual");assertThat(r.laborAllocated()).isEqualTo("100");
            assertThat(r.totalCost()).isEqualTo("130");assertThat(r.personalRevenue()).isEqualTo("168");
        });
        assertThat(turnkey.comparison(s,s.projects().get(0)).items()).anyMatch(i->i.developerId().equals("contract-person")&&i.type().equals("NO_ASSIGNMENT"));
        assertThat(result.knownPersonalRevenue()).isNotEqualTo("999");
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"0"})
    void missingBudgetKeepsKnownCostButRevenueAndProfitUnknown(String budget) {
        var result=calculate(tk(budget,assignment("a","1.00")));
        assertThat(result.rows()).singleElement().satisfies(r->{
            assertThat(r.totalCost()).isEqualTo("130");assertThat(r.personalRevenue()).isNull();
            assertThat(r.profit()).isNull();assertThat(r.costComplete()).isTrue();assertThat(r.revenueComplete()).isFalse();
        });
        assertThat(result.completeness().costComplete()).isTrue();
        assertThat(result.completeness().revenueComplete()).isFalse();
        assertThat(result.completeness().issues()).anyMatch(i->i.message().contains("예산 미확인")&&i.source().equals("TB_PJ_PROJ"));
    }
    @Test void overflowIsAnAffectedRevenueDiagnosticAndDoesNotEraseKnownCost() {
        var p=project("TK","ACT");
        var base=tk("1",assignment("a","1.00"));
        var large=new Project(p.projectId(),p.name(),p.contractType(),p.status(),p.loseDate(),p.fromDate(),p.toDate(),n("999999999999999"),n("1"),n("0"),n("0"));
        var s=new Sources(base.period(),base.actor(),base.asOfDate(),base.people(),base.prices(),base.states(),base.payroll(),base.assignments(),base.projectMonths(),
            List.of(large),base.projectParts(),base.turnkey(),base.storedCosts(),base.standards(),base.parts(),base.closes(),base.allowanceCodes());
        var result=calculate(s);
        assertThat(result.rows().get(0).personalRevenue()).isNull();
        assertThat(result.rows().get(0).totalCost()).isEqualTo("100");
        assertThat(result.completeness().issues()).anyMatch(i->i.message().contains("NUMERIC(15)"));
    }
    @Test void turnkeyExclusionResolvesOnlyRevenueUnknownAndLeavesOriginalUntouched() {
        var original=calculate(tk(null,assignment("a","1.00")));
        var view=turnkey.project(original,false);
        assertThat(view.rows().get(0).personalRevenue()).isEqualTo("0");
        assertThat(view.rows().get(0).profit()).isEqualTo("-130");
        assertThat(view.completeness().revenueComplete()).isTrue();
        assertThat(view.rows().get(0).totalCost()).isEqualTo(original.rows().get(0).totalCost());
        assertThat(view.assignmentMm()).isEqualTo(original.assignmentMm());
        assertThat(original.rows().get(0).personalRevenue()).isNull();
        assertThat(original.completeness().revenueComplete()).isFalse();
        var s=tk(null,assignment("a","1.00"));
        var unknown=new Sources(s.period(),s.actor(),s.asOfDate(),s.people(),s.prices(),s.states(),List.of(),s.assignments(),s.projectMonths(),s.projects(),
            s.projectParts(),s.turnkey(),s.storedCosts(),s.standards(),s.parts(),s.closes(),s.allowanceCodes());
        var unknownView=turnkey.project(calculate(unknown),false);
        assertThat(unknownView.completeness().costComplete()).isFalse();
        assertThat(unknownView.rows().get(0).profit()).isNull();
    }
    @ParameterizedTest @ValueSource(strings={"MO","DP"}) void monthlySalesAreUsedExactlyOnce(String type) {
        var p=person("FL");var s=sources(p,List.of(pay("FL","100")),List.of(assignment("a","0.50")),List.of(project(type,"ACT")));
        var result=calculate(s);
        assertThat(result.rows().get(0).personalRevenue()).isEqualTo("4970000");
        assertThat(result.rows().get(0).fixed()).isEqualTo("N");
        assertThat(result.rows().get(0).regDttm()).isNull();
    }
    @ParameterizedTest @ValueSource(strings={"PRE","LOSE"}) void nonBillableProjectStatusesNeverGainTurnkeyRevenue(String status) {
        var p=person("FL");var s=sources(p,List.of(pay("FL","100")),List.of(assignment("a","0.50")),List.of(project("TK",status)));
        assertThat(calculate(cells(s,List.of(cell("PLAN",MONTH,"DEV","actual","101")))).rows().get(0).personalRevenue()).isEqualTo("0");
    }
}
