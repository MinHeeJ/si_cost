package com.sicost.profitability;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.shared.SharedQueryDtos.CloseRow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.sicost.profitability.MonthlyCostCalculationTest.*;
import static com.sicost.profitability.MonthlyCostDtos.*;
import static org.assertj.core.api.Assertions.*;

/** T077–T079: pure inputs reuse the allocation suite; no database seeds or source writes. */
class MonthlyCostAllocationRegressionTest {
    private final MonthlyCostCalculation calculation = new MonthlyCostCalculation(new EstimatedPayrollAdapter());

    private Sources evidence(Sources s, List<ProjectPart> parts, List<StoredCost> stored, List<CloseRow> closes) {
        return new Sources(s.period(), s.actor(), s.asOfDate(), s.people(), s.prices(), s.states(), s.payroll(),
            s.assignments(), s.projectMonths(), s.projects(), parts, s.turnkey(), stored, s.standards(),
            s.parts(), closes, s.allowanceCodes());
    }

    private StoredCost snapshot(String cls, String fixed, String amount) {
        return new StoredCost(MONTH, "actual", "project", "historical", n("0.60"), n("0.00"),
            BigDecimal.ZERO, n(amount), n("12"), n("34"), n("46"), cls, "MO", "historical-part", fixed,
            "actor", LocalDateTime.of(2026, 1, 31, 12, 0), null, null, "현재 실주 프로젝트", "JOB", "PAID");
    }

    @Test void closedPreRemainsPreWithOriginalAmountsAndAuditAfterProjectLossOnEveryRead() {
        var person = person("FT");
        var base = sources(person, List.of(pay("FT", "0")), List.of(assignment("historical", "0.60")), List.of(project("MO", "LOSE")));
        var original = snapshot("PRE", "N", "4970000");
        var closed = evidence(base, base.projectParts(), List.of(original), List.of(new CloseRow(MONTH, "CLOSE")));
        var first = closed.protectedRows(MONTH, person.personId());
        assertThat(first).singleElement().satisfies(row -> {
            assertThat(row.costClass()).isEqualTo("PRE");
            assertThat(row.laborAllocated()).isEqualTo("4970000");
            assertThat(row.teamExpense()).isEqualTo("12");
            assertThat(row.projectExpense()).isEqualTo("34");
            assertThat(row.projectSalesPartId()).isEqualTo("historical-part");
            assertThat(row.fixed()).isEqualTo("N");
            assertThat(row.rowGenerated()).isFalse();
            assertThat(row.regDttm().toLocalDateTime()).isEqualTo(original.regDttm());
        });
        assertThat(closed.protectedRows(MONTH, person.personId())).isEqualTo(first);
        assertThatThrownBy(() -> calculation.calculate(closed, person, MONTH)).isInstanceOf(IllegalArgumentException.class);
        assertThat(closed.assignments()).isEqualTo(base.assignments());
        assertThat(closed.storedCosts()).containsExactly(original);
        assertThatThrownBy(() -> first.clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void fixedSnapshotUsesSameReadBoundaryAndMissingClosedEvidenceDoesNotGenerateZero() {
        var person = person("FT");
        var base = sources(person, List.of(pay("FT", "4970000")), List.of(), List.of());
        var fixed = evidence(base, List.of(), List.of(snapshot("PRE", "Y", "0")), List.of());
        assertThat(fixed.protectedRows(MONTH, person.personId())).singleElement().satisfies(row -> {
            assertThat(row.laborAllocated()).isEqualTo("0");
            assertThat(row.fixed()).isEqualTo("Y");
            assertThat(row.costClass()).isEqualTo("PRE");
        });
        var missing = evidence(base, List.of(), List.of(), List.of(new CloseRow(MONTH, "CLOSE")));
        assertThat(missing.protectedRows(MONTH, person.personId())).isEmpty();
        assertThatThrownBy(() -> base.protectedRows(MONTH, person.personId())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void projectLossDoesNotReconstructAssignmentsAbsentFromMonthlySourceOrChangeDates() {
        var person = person("FT");
        var base = sources(person, List.of(pay("FT", "4970000")), List.of(), List.of(project("MO", "LOSE")));
        assertThat(calculation.calculate(base, person, MONTH).rows()).singleElement()
            .satisfies(row -> assertThat(row.assignmentId()).isEqualTo("IDLE"));
        var assignment = assignment("a", "0.60");
        var withMonthlySource = sources(person, base.payroll(), List.of(assignment), base.projects());
        var first = calculation.calculate(withMonthlySource, person, MONTH);
        assertThat(first.rows().get(0).assignmentId()).isEqualTo(assignment.assignmentId());
        assertThat(first.rows().get(0).assignmentMm()).isEqualTo("0.60");
        assertThat(calculation.calculate(withMonthlySource, person, MONTH)).isEqualTo(first);
        assertThat(withMonthlySource.assignments()).containsExactly(assignment);
        assertThat(assignment.fromDate()).isEqualTo(parseMonth(MONTH).atDay(1));
        assertThat(assignment.toDate()).isEqualTo(parseMonth(MONTH).atEndOfMonth());
    }

    @Test void latestEffectiveProjectPartIsSeparateFromPersonPartAndPersonFilter() {
        var person = person("FT");
        var base = sources(person, List.of(pay("FT", "4970000")), List.of(assignment("a", "0.60")), List.of(project("MO", "ACT")));
        var result = calculation.calculate(base, person, MONTH);
        assertThat(result.rows().get(0).projectSalesPartId()).isEqualTo("project-part");
        assertThat(result.rows().get(1).projectSalesPartId()).isNull();
        assertThat(person.matchesPart("person-part")).isTrue();
        assertThat(person.matchesPart("project-part")).isFalse();
        assertThat(person.matchesPart("UNASSIGNED")).isFalse();
        assertThat(person.matchesPart(null)).isTrue();
        var unassigned = new Person(person.personId(), person.managementNo(), person.name(), person.employeeType(),
            person.companyId(), person.companyName(), person.gradeCode(), person.jobCode(), person.positionCode(),
            person.position(), person.directClass(), null, null, person.hireDate(), person.retireDate(), person.workStatus(), person.useYn());
        assertThat(unassigned.matchesPart("UNASSIGNED")).isTrue();
        assertThat(unassigned.matchesPart("project-part")).isFalse();
        var historical = evidence(base, List.of(new ProjectPart("project", "202602", "future-part"),
            new ProjectPart("project", "202412", "old-part"), new ProjectPart("project", "202512", "latest-part")), List.of(), List.of());
        assertThat(calculation.calculate(historical, person, MONTH).rows().get(0).projectSalesPartId()).isEqualTo("latest-part");
    }

    @Test void noEffectivePartAndExplicitNullNeverFallBackToOlderOrPersonPartAndInternalAlwaysNull() {
        var person = person("FT");
        var base = sources(person, List.of(pay("FT", "0")), List.of(assignment("a", "1.00")), List.of(project("MO", "ACT")));
        for (var parts : List.of(List.<ProjectPart>of(), List.of(new ProjectPart("project", "202602", "future")),
            List.of(new ProjectPart("project", "202512", "old"), new ProjectPart("project", MONTH, null)))) {
            assertThat(calculation.calculate(evidence(base, parts, List.of(), List.of()), person, MONTH).rows().get(0).projectSalesPartId()).isNull();
        }
        var internal = sources(person, base.payroll(), base.assignments(), List.of(project("IN", "ACT")));
        assertThat(calculation.calculate(internal, person, MONTH).rows().get(0).projectSalesPartId()).isNull();
    }

    @Test void freeAndIdleMmAndNullableTypesAreExplicitInSerializedZeroCostResult() throws Exception {
        var person = person("FT");
        var assignment = assignment("a", "0.60");
        var free = new Assignment(assignment.month(), assignment.personId(), assignment.projectId(), assignment.assignmentId(),
            assignment.assignmentMm(), assignment.appliedUnitPrice(), assignment.salesAmount(), assignment.fixed(), assignment.jobCode(),
            "FREE", assignment.contractPersonId(), assignment.status(), assignment.fromDate(), assignment.toDate());
        var result = calculation.calculate(sources(person, List.of(pay("FT", "0")), List.of(free), List.of(project("MO", "ACT"))), person, MONTH);
        assertThat(result.rows().get(0).freeMm()).isEqualTo(result.rows().get(0).assignmentMm());
        var idle = result.rows().get(1);
        assertThat(idle.assignmentMm()).isEqualTo("0.40");
        assertThat(idle.freeMm()).isEqualTo("0.00");
        assertThat(idle.inputType()).isNull();
        assertThat(idle.contractType()).isNull();
        assertThat(idle.laborAllocated()).isEqualTo("0");
        assertThat(result.idleRowGenerated()).isTrue();
        var json = JsonMapper.builder().findAndAddModules().build().valueToTree(result);
        assertThat(json.path("idleRowGenerated").booleanValue()).isTrue();
        assertThat(json.path("rows").get(1).path("rowGenerated").booleanValue()).isTrue();
        assertThat(json.path("rows").get(1).path("inputType").isNull()).isTrue();
        assertThat(json.path("rows").get(1).path("laborAllocated").textValue()).isEqualTo("0");
        var noIdle = calculation.calculate(sources(person, List.of(pay("FT", "0")), List.of(assignment("a", "1.00")), List.of(project("MO", "ACT"))), person, MONTH);
        assertThat(noIdle.idleRowGenerated()).isFalse();
        assertThat(noIdle.rows()).hasSize(1);
        assertThat(noIdle.rows().get(0).freeMm()).isEqualTo("0.00");
        var noRowPerson = person("FL");
        var noRows = calculation.calculate(sources(noRowPerson, List.of(pay("FL", "0")), List.of(), List.of()), noRowPerson, MONTH);
        assertThat(noRows.rows()).isEmpty();
        assertThat(noRows.idleRowGenerated()).isFalse();
        assertThat(noRows.indirectRowGenerated()).isFalse();
    }

    @Test void zeroIndirectHasOneGeneratedZeroMmRowRatherThanAbsentEvidence() {
        var person = person("FT");
        var original = pay("FT", "0");
        var indirect = new Payroll(original.month(), original.personId(), original.pay(), original.allowance(), original.companyInsurance(),
            original.selfInsurance(), original.retirementReserve(), original.incomeTax(), original.withholdingTax(), original.netPay(),
            original.vendorSupply(), original.employeeType(), "INDRCT", original.uploadId(), original.regUserId(), original.regDttm(), original.modUserId(), original.modDttm());
        var result = calculation.calculate(sources(person, List.of(indirect), List.of(assignment("a", "1.50")), List.of(project("MO", "ACT"))), person, MONTH);
        assertThat(result.indirectRowGenerated()).isTrue();
        assertThat(result.idleRowGenerated()).isFalse();
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.costClass()).isEqualTo("INDRCT");
            assertThat(row.assignmentMm()).isEqualTo("0.00");
            assertThat(row.freeMm()).isEqualTo("0.00");
            assertThat(row.laborAllocated()).isEqualTo("0");
            assertThat(row.projectSalesPartId()).isNull();
            assertThat(row.inputType()).isNull();
            assertThat(row.contractType()).isNull();
            assertThat(row.rowGenerated()).isTrue();
        });
    }
}
