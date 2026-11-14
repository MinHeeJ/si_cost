package com.sicost.salespartsettlements;

import com.sicost.profitability.*;
import java.math.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.sicost.profitability.MonthlyCostDtos.*;
import static com.sicost.salespartsettlements.SalesPartSettlementDtos.*;
import static com.sicost.salespartsettlements.SalesPartSettlementQueryTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SalesPartSettlementSourcesTest {
    final EstimatedPayrollAdapter payroll=new EstimatedPayrollAdapter();
    final TurnkeyRevenueCalculation turnkey=new TurnkeyRevenueCalculation();
    final SalesPartSettlementSources calculator=new SalesPartSettlementSources(new MonthlyCostService(mock(MonthlyCostRepository.class),new MonthlyCostCalculation(payroll),turnkey,payroll),turnkey);
    SalesPartSettlementRepository.Snapshot data(String month,String type,String pay,String base,LocalDate first) {
        var f=fixture(month,1);var s=f.sources();var original=s.people().get(0);
        var person=new Person(original.personId(),original.managementNo(),original.name(),type,null,null,null,"JOB",null,null,"DRCT","part","파트",null,null,"RETIRE","N");
        var pays=pay==null?List.<Payroll>of():List.of(new Payroll(month,"dev-0",n(pay),null,null,null,null,null,null,null,n(pay),type,"DRCT",null,null,null,null,null));
        var source=new Sources(s.period(),s.actor(),s.asOfDate(),List.of(person),List.of(new Price("dev-0","202601",null,n(base),null)),s.states(),pays,s.assignments(),s.projectMonths(),s.projects(),s.projectParts(),s.turnkey(),s.storedCosts(),s.standards(),s.parts(),s.closes(),s.allowanceCodes());
        return replace(f,source,List.of(new FirstInput("dev-0",first)));
    }
    SalesPartSettlementRepository.Snapshot replace(SalesPartSettlementRepository.Snapshot f,Sources s,List<FirstInput> first) {
        return new SalesPartSettlementRepository.Snapshot(s,f.parts(),f.standards(),f.history(),f.parents(),f.details(),first,f.revisionSources());
    }
    List<Row> rows(SalesPartSettlementRepository.Snapshot f) {
        var standard=SalesPartSettlementService.standard(f,"part",f.sources().period().toMonth());
        return calculator.calculate(f,new Tab("part","파트",true,false,"WORK",standard,List.of()),new ArrayList<>());
    }
    @ParameterizedTest @ValueSource(strings={"FT","CT","FL","OS"})
    void laterUploadedPayIsFullOnceAndOnlyFtReceivesPartRetirement(String type) {
        var result=rows(data("202607",type,"120","900",LocalDate.of(2026,1,1))).get(0);
        assertThat(result.payAmount()).isEqualTo("120");
        assertThat(result.retirementAmount()).isEqualTo(type.equals("FT")?"10":"0");
        assertThat(result.salesExpenseAmount()).isEqualTo(type.equals("OS")?"0":"10");
        assertThat(result.mm()).isEqualTo("0.50");
    }
    @ParameterizedTest @ValueSource(strings={"2026-07-01","2026-07-31","2028-02-29"})
    void startMonthUsesCalendarBaseNotUploadedAlreadyProratedPay(String start) {
        LocalDate date=LocalDate.parse(start);String month=start.substring(0,7).replace("-","");
        var result=rows(data(month,"FT","7","3100",date)).get(0);
        YearMonth ym=YearMonth.from(date);
        String expected=n("3100").multiply(BigDecimal.valueOf(ym.lengthOfMonth()-date.getDayOfMonth()+1)).divide(BigDecimal.valueOf(ym.lengthOfMonth()),0,RoundingMode.DOWN).toPlainString();
        assertThat(result.payAmount()).isEqualTo(expected);assertThat(result.basis().paySource()).isEqualTo("START_MONTH_BASE");assertThat(result.salesExpenseAmount()).isEqualTo("10");
    }
    @Test void firstMonthMissingBaseIsWarningZeroButExplicitZeroIsNotMissing() {
        var missing=rows(data("202607","FT","120",null,LocalDate.of(2026,7,1))).get(0);
        assertThat(missing.payAmount()).isEqualTo("0");assertThat(missing.basis().firstMonthMissingPay()).isTrue();assertThat(missing.issues()).noneMatch(Issue::blocking);
        var zero=rows(data("202607","FT",null,"0",LocalDate.of(2026,7,1))).get(0);
        assertThat(zero.payAmount()).isEqualTo("0");assertThat(zero.basis().firstMonthMissingPay()).isFalse();
    }
    @Test void otherMonthMissingPayPropagatesOnlyDependentAmountsAndBlocksConfirmation() {
        var result=rows(data("202607","FT",null,null,LocalDate.of(2026,1,1))).get(0);
        assertThat(result.mm()).isEqualTo("0.50");assertThat(result.unitPrice()).isEqualTo("1000");assertThat(result.payAmount()).isNull();assertThat(result.costAmount()).isNull();assertThat(result.profitShareAmount()).isNull();assertThat(result.issues()).anyMatch(Issue::blocking);
    }
    @Test void absentUploadUsesEffectiveEstimatedBaseAndNeverCompanyPayrollFormula() {
        var result=rows(data("202607","FT",null,"120",LocalDate.of(2026,1,1))).get(0);
        assertThat(result.payAmount()).isEqualTo("120");assertThat(result.basis().estimated()).isTrue();assertThat(result.basis().paySource()).isEqualTo("ESTIMATED_BASE");assertThat(result.basis().warnings()).anyMatch(i->i.code().equals("ESTIMATED_PAY"));
    }
    @Test void overlappingBasePayIsNotHiddenByLatestRow() {
        var f=data("202607","FT",null,"120",LocalDate.of(2026,1,1));var s=f.sources();
        var changed=new Sources(s.period(),s.actor(),s.asOfDate(),s.people(),List.of(s.prices().get(0),new Price("dev-0","202606",null,n("500"),null)),s.states(),s.payroll(),s.assignments(),s.projectMonths(),s.projects(),s.projectParts(),s.turnkey(),s.storedCosts(),s.standards(),s.parts(),s.closes(),s.allowanceCodes());
        var result=rows(replace(f,changed,f.firstInputs())).get(0);assertThat(result.payAmount()).isNull();assertThat(result.issues()).anyMatch(i->i.code().equals("PAY_STANDARD_OVERLAP"));
    }
    @ParameterizedTest @ValueSource(strings={"PRE","LOSE","END"})
    void preLoseAndEndedProjectsKeepActualMmAndPay(String status) {
        var f=data("202607","FT","120",null,LocalDate.of(2026,1,1));var s=f.sources();var project=s.projects().get(0);
        var changedProject=new Project(project.projectId(),project.name(),project.contractType(),status,null,project.fromDate(),project.toDate(),project.contractAmount(),project.budget(),null,null);
        var changed=new Sources(s.period(),s.actor(),s.asOfDate(),s.people(),s.prices(),s.states(),s.payroll(),s.assignments(),s.projectMonths(),List.of(changedProject),s.projectParts(),s.turnkey(),s.storedCosts(),s.standards(),s.parts(),s.closes(),s.allowanceCodes());
        var result=rows(replace(f,changed,f.firstInputs())).get(0);assertThat(result.mm()).isEqualTo("0.50");assertThat(result.payAmount()).isEqualTo("120");assertThat(result.unitPrice()).isEqualTo(status.equals("END")?"1000":"0");
    }
    @Test void wholeMonthLeaveAndInternalOnlyHaveNoSettlementPopulation() {
        var f=data("202607","FT","120",null,LocalDate.of(2026,1,1));var s=f.sources();
        var leave=new Sources(s.period(),s.actor(),s.asOfDate(),s.people(),s.prices(),List.of(new WorkState("dev-0","1","LEAVE",LocalDate.of(2026,7,1),LocalDate.of(2026,7,31))),s.payroll(),s.assignments(),s.projectMonths(),s.projects(),s.projectParts(),s.turnkey(),s.storedCosts(),s.standards(),s.parts(),s.closes(),s.allowanceCodes());
        assertThat(rows(replace(f,leave,f.firstInputs()))).isEmpty();
        var p=s.projects().get(0);var internal=new Project(p.projectId(),p.name(),"IN",p.status(),p.loseDate(),p.fromDate(),p.toDate(),p.contractAmount(),p.budget(),p.teamExpense(),p.projectExpense());
        var onlyInternal=new Sources(s.period(),s.actor(),s.asOfDate(),s.people(),s.prices(),s.states(),s.payroll(),s.assignments(),s.projectMonths(),List.of(internal),s.projectParts(),s.turnkey(),s.storedCosts(),s.standards(),s.parts(),s.closes(),s.allowanceCodes());
        assertThat(rows(replace(f,onlyInternal,f.firstInputs()))).isEmpty();
    }
    @Test void negativeProfitUsesSignedDownAndZeroPeopleCardIsSubtractedOnce() {
        var standard=new com.sicost.salespartsettlements.SalesPartSettlementDtos.Standard(null,null,"40","0",null,true);
        var amounts=SalesPartSettlementCalculator.amounts("OS",n("4"),n("0"),standard);
        assertThat(amounts.profit()).isEqualTo("-4");assertThat(amounts.share()).isEqualTo("-1");
        assertThat(SalesPartSettlementCalculator.totals(List.of(),"5",null,false).partProfitAmount()).isEqualTo("-5");
    }
}
