package com.sicost.profitability;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.sicost.profitability.MonthlyCostDtos.*;
import static org.assertj.core.api.Assertions.*;

/** Pure boundary inputs only; these are not database seed rows or the unresolved OQ datasets. */
class MonthlyCostCalculationTest {
    final EstimatedPayrollAdapter payroll = new EstimatedPayrollAdapter();
    final MonthlyCostCalculation calculation = new MonthlyCostCalculation(payroll);
    static final String MONTH = "202601";
    static BigDecimal n(String value) { return value == null ? null : new BigDecimal(value); }
    static Person person(String type) {
        return new Person("actual", "owned", "시험", type, null, null, null, "JOB", null, null,
            "DRCT", "person-part", "사람 파트", null, null, "WORK", "N");
    }
    static Payroll pay(String type, String labor) {
        return new Payroll(MONTH, "actual", n(labor), BigDecimal.ZERO, BigDecimal.ZERO, null,
            BigDecimal.ZERO, null, null, null, n(labor), type, "DRCT", null, null, null, null, null);
    }
    static Assignment assignment(String id, String mm) {
        return new Assignment(MONTH, "actual", "project", id, n(mm), n("4970000"), n("4970000"), "N",
            "JOB", "PAID", "contract-person", "ACT", LocalDate.of(2026,1,1), LocalDate.of(2026,1,31));
    }
    static Project project(String type, String status) {
        return new Project("project", "프로젝트", type, status, null, LocalDate.of(2026,1,1), LocalDate.of(2026,12,31),
            n("4970000"), n("4970000"), BigDecimal.ZERO, BigDecimal.ZERO);
    }
    static Sources sources(Person p, List<Payroll> pay, List<Assignment> assignments, List<Project> projects) {
        return new Sources(new ProfitabilityPeriod(2026, MONTH, MONTH, MONTH, OffsetDateTime.parse("2026-01-31T00:00:00+09:00"), false),
            "actor", LocalDate.of(2026,1,31), List.of(p), List.of(), List.of(), pay, assignments, assignments, projects,
            List.of(new ProjectPart("project", "202501", "old-project-part"), new ProjectPart("project", MONTH, "project-part"),
                new ProjectPart("project", "202602", "future-project-part")), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
    static Sources withStates(Sources s, List<WorkState> states) {
        return new Sources(s.period(), s.actor(), s.asOfDate(), s.people(), s.prices(), states, s.payroll(), s.assignments(), s.projectMonths(),
            s.projects(), s.projectParts(), s.turnkey(), s.storedCosts(), s.standards(), s.parts(), s.closes(), s.allowanceCodes());
    }
    MonthlyPersonCost calculate(String type, String labor, Assignment... rows) {
        Person p = person(type);
        return calculation.calculate(sources(p, List.of(pay(type,labor)), List.of(rows), List.of(project("MO", "ACT"))), p, MONTH);
    }
    @Test void mandatoryOverallocatedExampleConservesEveryWonAndUsesSortedLastAssignment() {
        var result = calculate("FT", "4970000", assignment("b","0.50"), assignment("a","1.00"));
        assertThat(result.rows()).extracting(MonthlyCostRow::assignmentId).containsExactly("a", "b");
        assertThat(result.rows()).extracting(MonthlyCostRow::laborAllocated).containsExactly("3313333", "1656667");
        assertThat(result.knownLaborAllocated()).isEqualTo("4970000");
        assertThat(result.idleRowGenerated()).isFalse();
        assertThat(result.completeness().allocationComplete()).isTrue();
    }
    @Test void mandatoryPartialMmIsNotRecalculatedAndIdleGetsOnlyLaborResidual() {
        Assignment row = assignment("a", "0.60");
        var result = calculate("FT", "4970000", row);
        assertThat(result.rows()).extracting(MonthlyCostRow::laborAllocated).containsExactly("2982000", "1988000");
        assertThat(result.rows()).extracting(MonthlyCostRow::assignmentMm).containsExactly("0.60", "0.40");
        assertThat(row.assignmentMm().toPlainString()).isEqualTo("0.60");
        assertThat(result.rows().get(1).directExpense()).isEqualTo("0");
        assertThat(result.rows().get(1).contractType()).isNull();
    }
    @Test void freelanceHalfMmGetsFullPaymentAndNeverIdle() {
        var result = calculate("FL", "5000000", assignment("a", "0.50"));
        assertThat(result.rows()).singleElement().satisfies(r -> assertThat(r.laborAllocated()).isEqualTo("5000000"));
        assertThat(result.idleRowGenerated()).isFalse();
    }
    @Test void exactlyOneMmUsesLastRealRowAndNoSyntheticIdleEvenForZero() {
        var result = calculate("CT", "0", assignment("a", "0.60"), assignment("b", "0.40"));
        assertThat(result.rows()).hasSize(2);
        assertThat(result.idleRowGenerated()).isFalse();
        assertThat(result.completeness().costComplete()).isTrue();
        assertThat(calculate("FT", "0").rows()).singleElement().satisfies(r -> {
            assertThat(r.projectId()).isEqualTo("IDLE"); assertThat(r.rowGenerated()).isTrue(); assertThat(r.laborAllocated()).isEqualTo("0");
        });
    }
    @Test void signedDownRoundsTowardZeroAndLastRowConservesNegativeLabor() {
        var result = calculate("FT", "-4970000", assignment("a", "1.00"), assignment("b", "0.50"));
        assertThat(result.rows()).extracting(MonthlyCostRow::laborAllocated).containsExactly("-3313333", "-1656667");
        assertThat(MonthlyCostCalculation.allocate(n("-1"), n("0.60"), n("1.00"))).isZero();
    }
    @Test void canceledAndZeroRowsAndContractPersonNeverReceiveActualPersonLabor() {
        var a = assignment("active", "0.60");
        var cancel = new Assignment(MONTH,"actual","project","cancel",n("1.00"),n("4970000"),n("4970000"),"N","JOB","FREE",null,"CNCL",a.fromDate(),a.toDate());
        var result = calculate("FT", "4970000", cancel, assignment("zero", "0.00"), a);
        assertThat(result.rows()).extracting(MonthlyCostRow::personId).containsOnly("actual");
        assertThat(result.rows()).extracting(MonthlyCostRow::assignmentId).containsExactly("active", "IDLE");
        var p = person("FT"); var s = sources(p,List.of(pay("FT","4970000")),List.of(a),List.of(project("MO","ACT")));
        assertThat(MonthlyCostCalculation.validAssignments(s.assignments(), "contract-person", MONTH)).isEmpty();
        assertThat(result.assignmentMm()).isEqualTo("0.60");
    }
    @ParameterizedTest @ValueSource(strings={"FL","OS"}) void noAssignmentsNeverInventRowsForNonSalariedPayment(String type) {
        var result = calculate(type, "5000000");
        assertThat(result.rows()).isEmpty(); assertThat(result.completeness().allocationComplete()).isFalse();
        assertThat(result.completeness().issues()).anySatisfy(i -> assertThat(i.sourceAmount()).isEqualTo("5000000"));
        assertThat(calculate(type,"-5000000").completeness().allocationComplete()).isFalse();
        assertThat(calculate(type,"0").completeness().allocationComplete()).isTrue();
    }
    @Test void wholeLeaveUnionAllocatesOnlyIdleAndDiagnosesOverlapWhilePartialLeaveKeepsMm() {
        var p = person("FT"); var a = assignment("a", "0.60");
        var s = sources(p,List.of(pay("FT","4970000")),List.of(a),List.of(project("MO","ACT")));
        var states = List.of(new WorkState("actual","1","LEAVE",a.fromDate(),LocalDate.of(2026,1,15)),
            new WorkState("actual","2","LEAVE",LocalDate.of(2026,1,16),a.toDate()));
        var result = calculation.calculate(withStates(s,states),p,MONTH);
        assertThat(result.rows()).singleElement().satisfies(r -> {
            assertThat(r.projectId()).isEqualTo("IDLE"); assertThat(r.personalRevenue()).isEqualTo("0"); assertThat(r.laborAllocated()).isEqualTo("4970000");
        });
        assertThat(result.completeness().issues()).anyMatch(i -> i.message().contains("월 전체 휴직·배정 겹침"));
        assertThat(calculation.calculate(withStates(s,List.of(states.get(0))),p,MONTH).rows().get(0).assignmentMm()).isEqualTo("0.60");
        var zero = sources(p,List.of(pay("FT","0")),List.of(a),s.projects());
        assertThat(calculation.calculate(withStates(zero,states),p,MONTH).knownLaborAllocated()).isEqualTo("0");
    }
    @Test void storedIndirectClassificationWinsOverCurrentMasterAndHasOneZeroMmRow() {
        var p = person("FT"); var pay = pay("FT","4970000");
        var indirect = new Payroll(pay.month(),pay.personId(),pay.pay(),pay.allowance(),pay.companyInsurance(),pay.selfInsurance(),pay.retirementReserve(),
            pay.incomeTax(),pay.withholdingTax(),pay.netPay(),pay.vendorSupply(),pay.employeeType(),"INDRCT",null,null,null,null,null);
        var result = calculation.calculate(sources(p,List.of(indirect),List.of(assignment("a","1.00")),List.of(project("MO","ACT"))),p,MONTH);
        assertThat(result.indirectRowGenerated()).isTrue();
        assertThat(result.rows()).singleElement().satisfies(r -> {
            assertThat(r.projectId()).isEqualTo("INDRCT"); assertThat(r.assignmentMm()).isEqualTo("0.00");
            assertThat(r.personalRevenue()).isEqualTo("0"); assertThat(r.directExpense()).isEqualTo("0");
        });
    }
    @Test void retirementMonthIncludedButFollowingMonthPaymentExcludedAndDiagnosed() {
        var original = person("FT");
        var p = new Person(original.personId(),original.managementNo(),original.name(),original.employeeType(),null,null,null,"JOB",null,null,
            "DRCT",original.personPartId(),original.personPartName(),null,LocalDate.of(2025,12,31),"RETIRE","N");
        var result = calculation.calculate(sources(p,List.of(pay("FT","4970000")),List.of(),List.of()),p,MONTH);
        assertThat(result.rows()).isEmpty(); assertThat(result.knownLaborAllocated()).isEqualTo("0");
        assertThat(result.completeness().issues()).anyMatch(i -> i.message().contains("재직기간 밖"));
        assertThat(p.employed("202512")).isTrue();
    }
    @Test void actualLosePrecedesInternalAndPreservesIdsAndExpensesWhileInternalIsRnd() {
        var p = person("FT"); var a = assignment("a","0.60");
        var base = project("IN","LOSE");
        var project = new Project(base.projectId(),base.name(),base.contractType(),base.status(),null,base.fromDate(),base.toDate(),base.contractAmount(),base.budget(),n("4970000"),n("5000000"));
        var s = sources(p,List.of(pay("FT","4970000")),List.of(a),List.of(project));
        var result = calculation.calculate(s,p,MONTH);
        assertThat(result.rows().get(0)).satisfies(r -> {
            assertThat(r.costClass()).isEqualTo("IDLE"); assertThat(r.projectId()).isEqualTo("project"); assertThat(r.assignmentId()).isEqualTo("a");
            assertThat(r.teamExpense()).isEqualTo("2982000"); assertThat(r.projectExpense()).isEqualTo("3000000");
            assertThat(r.directExpense()).isEqualTo("5982000"); assertThat(r.projectSalesPartId()).isNull();
        });
        assertThat(result.rows().get(1).directExpense()).isEqualTo("0");
        assertThat(MonthlyCostCalculation.costClass(project("IN","PRE"))).isEqualTo("RND");
        assertThat(MonthlyCostCalculation.costClass(project("MO","PRE"))).isEqualTo("PRE");
        assertThat(MonthlyCostCalculation.projectPart(s,project("MO","ACT"),MONTH)).isEqualTo("project-part");
        assertThat(p.personPartId()).isEqualTo("person-part");
    }
    @Test void freeKeepsExactMmAndExpenseAndOsSkipsAllExpenses() {
        var a = assignment("a","0.60");
        var free = new Assignment(a.month(),a.personId(),a.projectId(),a.assignmentId(),a.assignmentMm(),a.appliedUnitPrice(),a.salesAmount(),a.fixed(),a.jobCode(),"FREE",a.contractPersonId(),a.status(),a.fromDate(),a.toDate());
        var p = person("FL"); var base = project("MO","ACT");
        var expense = new Project(base.projectId(),base.name(),base.contractType(),base.status(),null,base.fromDate(),base.toDate(),base.contractAmount(),base.budget(),n("4970000"),n("5000000"));
        var result = calculation.calculate(sources(p,List.of(pay("FL","5000000")),List.of(free),List.of(expense)),p,MONTH);
        assertThat(result.rows().get(0).freeMm()).isEqualTo("0.60");
        assertThat(result.rows().get(0).personalRevenue()).isEqualTo("0");
        assertThat(result.rows().get(0).directExpense()).isEqualTo("5982000");
        var os = person("OS");
        assertThat(calculation.calculate(sources(os,List.of(pay("OS","5000000")),List.of(a),List.of(expense)),os,MONTH).rows().get(0).directExpense()).isEqualTo("0");
    }
    @Test void numericBoundariesDoNotClipRowSourcesOrUnboundedPeriodSums() {
        var max = calculate("FL","999999999999999",assignment("a","1.00"));
        assertThat(max.rows().get(0).laborAllocated()).isEqualTo("999999999999999");
        var overflow = calculate("FL","1000000000000000",assignment("a","1.00"));
        assertThat(overflow.rows().get(0).laborAllocated()).isNull();
        assertThat(overflow.rows().get(0).profit()).isNull();
        assertThat(overflow.completeness().costComplete()).isFalse();
        assertThat(overflow.completeness().issues()).anyMatch(i -> i.message().contains("NUMERIC(15)"));
        assertThat(new BigDecimal(max.knownLaborAllocated()).add(new BigDecimal(max.knownLaborAllocated())).toPlainString()).isEqualTo("1999999999999998");
    }
    @Test void expenseMultipliesEachActualMmWithoutRedistributionAndOverflowRemainsUnknown() {
        var p = person("FT"); var base = project("MO","ACT");
        var expense = new Project(base.projectId(),base.name(),base.contractType(),base.status(),null,base.fromDate(),base.toDate(),base.contractAmount(),base.budget(),n("4970000"),n("5000000"));
        var s = sources(p,List.of(pay("FT","4970000")),List.of(assignment("a","0.60"),assignment("b","0.60")),List.of(expense));
        var result = calculation.calculate(s,p,MONTH);
        assertThat(result.rows()).extracting(MonthlyCostRow::teamExpense).containsExactly("2982000","2982000");
        assertThat(result.rows()).extracting(MonthlyCostRow::projectExpense).containsExactly("3000000","3000000");
        assertThat(result.knownLaborAllocated()).isEqualTo("4970000");
        var large = new Project(base.projectId(),base.name(),base.contractType(),base.status(),null,base.fromDate(),base.toDate(),base.contractAmount(),base.budget(),n("999999999999999"),BigDecimal.ZERO);
        var unknown = calculation.calculate(sources(p,List.of(pay("FT","4970000")),List.of(assignment("a","2.00")),List.of(large)),p,MONTH);
        assertThat(unknown.rows().get(0).teamExpense()).isNull();
        assertThat(unknown.rows().get(0).laborAllocated()).isEqualTo("4970000");
        assertThat(unknown.rows().get(0).totalCost()).isNull();
        assertThat(unknown.completeness().costComplete()).isFalse();
        assertThat(large.teamExpense()).isEqualTo(n("999999999999999"));
        var negative = new Project(base.projectId(),base.name(),base.contractType(),base.status(),null,base.fromDate(),base.toDate(),base.contractAmount(),base.budget(),n("-1"),n("1"));
        var signed = calculation.calculate(sources(p,List.of(pay("FT","4970000")),List.of(assignment("a","0.60")),List.of(negative)),p,MONTH);
        assertThat(signed.rows().get(0).teamExpense()).isEqualTo("0");
        assertThat(signed.rows().get(0).projectExpense()).isEqualTo("0");
    }
    @Test void protectedMonthsCannotAccidentallyEnterOpenCalculation() {
        var p = person("FT"); var base = sources(p,List.of(pay("FT","4970000")),List.of(assignment("a","1.00")),List.of(project("MO","LOSE")));
        var closed = new Sources(base.period(),base.actor(),base.asOfDate(),base.people(),base.prices(),base.states(),base.payroll(),base.assignments(),base.projectMonths(),
            base.projects(),base.projectParts(),base.turnkey(),base.storedCosts(),base.standards(),base.parts(),
            List.of(new com.sicost.shared.SharedQueryDtos.CloseRow(MONTH,"CLOSE")),base.allowanceCodes());
        assertThatThrownBy(() -> calculation.calculate(closed,p,MONTH)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("사본");
        assertThat(closed.assignments()).isEqualTo(base.assignments());
        assertThat(closed.payroll()).isEqualTo(base.payroll());
    }
    @Test void ratesAndAverageHeadcountsUseHalfUpOneDecimalAndNeverTurnUnknownIntoZero() {
        assertThat(MonthlyCostCalculation.average(n("1"), n("4"))).isEqualTo(n("0.3"));
        assertThat(MonthlyCostCalculation.rate(n("1"), n("3"))).isEqualTo(n("33.3"));
        assertThat(MonthlyCostCalculation.rate(n("1"), BigDecimal.ZERO)).isNull();
        assertThat(MonthlyCostCalculation.average(n("1"), BigDecimal.ZERO)).isNull();
    }
    @Test void sameCompanySourcesAlwaysProduceEqualRowsAndCompletenessWithoutMutatingInputs() {
        var p = person("FT"); var s = sources(p,List.of(pay("FT","4970000")),List.of(assignment("a","0.60")),List.of(project("MO","ACT")));
        var first = calculation.calculate(s,p,MONTH);
        assertThat(calculation.calculate(s,p,MONTH)).isEqualTo(first);
        assertThat(s.assignments().get(0).assignmentMm()).isEqualTo(n("0.60"));
        assertThat(s.payroll().get(0).pay()).isEqualTo(n("4970000"));
        assertThatThrownBy(() -> first.rows().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
