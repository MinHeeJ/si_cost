package com.sicost.profitability;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import static com.sicost.profitability.MonthlyCostDtos.*;
import static com.sicost.profitability.MonthlyCostCalculationTest.*;
import static org.assertj.core.api.Assertions.*;

class IndividualProfitabilityCalculationTest {
    final TurnkeyRevenueCalculation turnkey = new TurnkeyRevenueCalculation();
    final IndividualProfitabilityCalculation calculation = new IndividualProfitabilityCalculation(turnkey);
    final EstimatedPayrollAdapter payroll = new EstimatedPayrollAdapter();
    final MonthlyCostService monthly = new MonthlyCostService(null, new MonthlyCostCalculation(payroll), turnkey, payroll);
    static IndividualProfitabilityInput.Query query(String... pairs) {
        var values = new LinkedMultiValueMap<String,String>();
        for (int i=0;i<pairs.length;i+=2) values.add(pairs[i],pairs[i+1]);
        return IndividualProfitabilityInput.query(values,false);
    }
    static Sources annual(Sources s, String end, List<Payroll> pay, List<Assignment> assignments) {
        return new Sources(new ProfitabilityPeriod(2026,MONTH,MONTH,end,s.period().calculatedAt(),false),s.actor(),
            parseMonth(end).atEndOfMonth(),s.people(),s.prices(),s.states(),pay,assignments,assignments,s.projects(),s.projectParts(),
            s.turnkey(),s.storedCosts(),s.standards(),s.parts(),s.closes(),s.allowanceCodes());
    }
    static Payroll payAt(String month, String type, String value) {
        return new Payroll(month,"actual",n(value),n("0"),n("0"),n("0"),n("0"),n("0"),n("0"),n(value),n(value),type,"DRCT",null,null,null,null,null);
    }
    static Assignment at(String month, String id, String mm) {
        var a=assignment(id,mm);
        return new Assignment(month,a.personId(),a.projectId(),id,a.assignmentMm(),a.appliedUnitPrice(),a.salesAmount(),a.fixed(),a.jobCode(),
            a.inputType(),a.contractPersonId(),a.status(),parseMonth(month).atDay(1),parseMonth(month).atEndOfMonth());
    }
    static Person identity(String id, String name, String grade) {
        return new Person(id,"number-"+id,name,"FT",null,null,grade,"JOB",null,null,"DRCT","person-part","사람 파트",null,null,"WORK","N");
    }
    List<IndividualProfitabilityPerson> rows(Sources s, IndividualProfitabilityInput.Query q) {
        return calculation.people(s,monthly.calculate(s),q);
    }
    @Test void laborAndSalaryAreCountedOnceAndSyntheticIdleNeverAddsMm() {
        var s=sources(person("FT"),List.of(pay("FT","4970000")),List.of(assignment("a","0.60"),assignment("b","0.20")),List.of(project("MO","ACT")));
        var p=rows(s,query()).get(0);
        assertThat(p.metrics().personalCost()).isEqualTo("4970000");
        assertThat(p.metrics().pay()).isEqualTo("4970000");assertThat(p.metrics().mm()).isEqualTo("0.80");
        assertThat(p.monthBasis().get(0).assignmentCount()).isEqualTo(2);
        assertThat(monthly.calculate(s).items().get(0).rows()).hasSize(3);
        assertThat(rows(s,query("show","CONCURRENT"))).hasSize(1);
    }
    @Test void tkExclusionChangesOnlyRevenueAndKnownProfitNotCostMmOrUtilization() {
        var s=sources(person("FT"),List.of(pay("FT","4970000")),List.of(assignment("a","0.40")),List.of(project("TK","ACT")));
        var before=rows(s,query()).get(0);var after=rows(s,query("includeTurnkey","false")).get(0);
        assertThat(after.turnkeyOnlyExcluded()).isTrue();assertThat(after.metrics().revenue()).isNull();
        assertThat(after.metrics().profit()).isEqualTo("-4970000");assertThat(after.metrics().profitRate()).isNull();
        assertThat(after.metrics().mm()).isEqualTo(before.metrics().mm());assertThat(after.metrics().totalCost()).isEqualTo(before.metrics().totalCost());
        assertThat(after.utilization()).isEqualTo(before.utilization()).isEqualTo("100.0");
        var summary=calculation.summary(s,monthly.calculate(s),List.of(after));
        assertThat(summary.validRevenuePersonCount()).isZero();assertThat(summary.unknownRevenueCount()).isZero();
        assertThat(summary.confirmedLossCount()).isEqualTo(1);
    }
    @Test void idleAndInternalZeroRemainValidRevenueAndNegativeCostProfit() {
        for(String type:List.of("MO","IN")) {
            var s=sources(person("FT"),List.of(pay("FT","100")),type.equals("IN")?List.of(assignment("a","0.40")):List.of(),List.of(project(type,"ACT")));
            var p=rows(s,query("includeTurnkey","false")).get(0);
            assertThat(p.metrics().revenue()).isEqualTo("0");assertThat(p.metrics().profit()).isEqualTo("-100");
            assertThat(p.metrics().profitRate()).isNull();assertThat(p.turnkeyOnlyExcluded()).isFalse();
        }
    }
    @Test void averageRecomputesFromTruncatedBasesRatherThanDividingFinalCostOrProfit() {
        var seed=sources(person("FT"),List.of(),List.of(),List.of(project("MO","ACT")));
        var s=annual(seed,"202602",List.of(payAt(MONTH,"FT","101"),payAt("202602","FT","102")),List.of(at(MONTH,"a","0.40")));
        var p=rows(s,query("view","MONTHLY_AVERAGE")).get(0);var m=p.metrics();
        assertThat(p.workedMonths()).isEqualTo(2);assertThat(m.personalCost()).isEqualTo("101");
        assertThat(n(m.totalCost())).isEqualByComparingTo(n(m.personalCost()).add(n(m.teamExpense())).add(n(m.projectExpense())));
        assertThat(n(m.profit())).isEqualByComparingTo(n(m.revenue()).subtract(n(m.totalCost())));
        assertThat(m.mm()).isEqualTo("0.20");assertThat(s.payroll().get(0).pay()).isEqualByComparingTo("101");
    }
    @Test void zeroNProducesNullAmountsNotFabricatedZeroAndFreelanceIdleMonthIsExcluded() {
        var s=sources(person("FL"),List.of(pay("FL","0")),List.of(),List.of());
        var p=rows(s,query("view","MONTHLY_AVERAGE")).get(0);
        assertThat(p.workedMonths()).isZero();assertThat(p.metrics().personalCost()).isNull();assertThat(p.metrics().profit()).isNull();
    }
    @Test void fullUnpaidLeaveExcludedFromNButPaidLeaveIncludedAndBothExcludeUtilizedMonth() {
        for(String amount:List.of("0","100")) {
            var seed=sources(person("FT"),List.of(),List.of(),List.of(project("MO","ACT")));
            var s=annual(seed,"202602",List.of(payAt(MONTH,"FT",amount),payAt("202602","FT","0")),List.of(at(MONTH,"a","0.40")));
            s=withStates(s,List.of(new WorkState("actual","owned-leave","LEAVE",LocalDate.of(2026,1,1),LocalDate.of(2026,1,31))));
            var p=rows(s,query()).get(0);
            assertThat(p.workedMonths()).isEqualTo(amount.equals("0")?1:2);assertThat(p.employmentMonths()).isEqualTo(2);
            assertThat(p.utilizedMonths()).isZero();assertThat(p.metrics().mm()).isEqualTo("0.00");
        }
    }
    @Test void assignmentsInDifferentMonthsDoNotMeanConcurrentAndZeroPointFourIsOneUtilizedMonth() {
        var s=annual(sources(person("FL"),List.of(),List.of(),List.of(project("MO","ACT"))),"202602",
            List.of(payAt(MONTH,"FL","100"),payAt("202602","FL","100")),List.of(at(MONTH,"a","0.40"),at("202602","b","0.40")));
        assertThat(rows(s,query("show","CONCURRENT"))).isEmpty();
        var p=rows(s,query()).get(0);assertThat(p.utilizedMonths()).isEqualTo(2);assertThat(p.workedMonths()).isEqualTo(2);
        assertThat(p.utilization()).isEqualTo(rows(s,query("view","MONTHLY_AVERAGE","includeTurnkey","false")).get(0).utilization());
    }
    @Test void nameIsCaseSensitiveTrimmedAndPersonPartIsNotProjectPart() {
        var s=sources(person("FT"),List.of(pay("FT","0")),List.of(),List.of());
        assertThat(rows(s,query("name","  시험 ","companyId","SELF","partId","person-part"))).hasSize(1);
        assertThat(rows(s,query("partId","project-part"))).isEmpty();assertThat(rows(s,query("partId","UNASSIGNED"))).isEmpty();
        var renamed=identity("actual","CaseSensitive",null);
        s=sources(renamed,s.payroll(),List.of(),List.of());
        assertThat(rows(s,query("name","case"))).isEmpty();assertThat(rows(s,query("name","Case"))).hasSize(1);
    }
    @Test void companyHeadcountAndHufDoNotFollowFilteredPopulationOrTkView() {
        var s=sources(person("FT"),List.of(pay("FT","100")),List.of(assignment("a","0.40")),List.of(project("TK","ACT")));
        var common=monthly.calculate(s);
        var full=calculation.summary(s,common,rows(s,query()));var empty=calculation.summary(s,common,rows(s,query("name","不存在")));
        assertThat(empty.companyHeadcount()).isEqualTo(full.companyHeadcount()).isEqualTo(1);
        assertThat(empty.companyUtilization()).isEqualTo(full.companyUtilization()).isEqualTo("100.0");
        assertThat(empty.averageHeadcount()).isEqualTo("1.0");assertThat(empty.averageEarnedHeadcount()).isEqualTo("0.0");
    }
    @Test void unknownMonthPropagatesCostProfitAndSummaryButRetainsMmAndConfirmedLossPolicy() {
        var s=sources(person("FT"),List.of(),List.of(assignment("a","0.40")),List.of(project("MO","ACT")));
        var p=rows(s,query()).get(0);assertThat(p.metrics().totalCost()).isNull();assertThat(p.metrics().profit()).isNull();
        assertThat(p.metrics().mm()).isEqualTo("0.40");assertThat(p.completeness().issues()).isNotEmpty();
        var summary=calculation.summary(s,monthly.calculate(s),List.of(p));assertThat(summary.averageCost()).isNull();
        assertThat(summary.unknownCostCount()).isEqualTo(1);assertThat(summary.confirmedLossCount()).isZero();
        assertThat(rows(s,query("show","LOSS"))).isEmpty();
    }
    @Test void nullLastNumericOrderingWorksBothWaysAndUsesValuesNotText() {
        var s=sources(person("FT"),List.of(pay("FT","100")),List.of(),List.of());var p=rows(s,query()).get(0);
        var unknown=rows(sources(person("FT"),List.of(),List.of(),List.of()),query()).get(0);
        for(String direction:List.of("ASC","DESC")) {
            var list=new ArrayList<>(List.of(unknown,p));list.sort(IndividualProfitabilityCalculation.order(query("sort","totalCost","direction",direction)));
            assertThat(list.get(0).metrics().totalCost()).isEqualTo("100");assertThat(list.get(1).metrics().totalCost()).isNull();
        }
        var small=rows(sources(person("FT"),List.of(pay("FT","2")),List.of(),List.of()),query()).get(0);
        var numeric=new ArrayList<>(List.of(p,small));numeric.sort(IndividualProfitabilityCalculation.order(query("sort","totalCost")));
        assertThat(numeric).extracting(x->x.metrics().totalCost()).containsExactly("2","100");
    }
    @Test void contributionIncludesUnclassifiedAndWholeJobUsesPeopleNotAverageOfGrades() {
        var s=sources(person("FT"),List.of(pay("FT","100")),List.of(),List.of());var p=rows(s,query()).get(0);
        var cells=calculation.contributions(List.of(p),List.of(new com.sicost.shared.SharedQueryDtos.CodeLookup("JOB","직무",null,null,null,null,null)),List.of());
        assertThat(cells).anySatisfy(c->{assertThat(c.gradeCode()).isEqualTo("UNCLASSIFIED");assertThat(c.personCount()).isEqualTo(1);assertThat(c.averageProfit()).isEqualTo("-100");});
        assertThat(cells).anySatisfy(c->{assertThat(c.gradeCode()).isEqualTo("ALL");assertThat(c.averageProfit()).isEqualTo("-100");});
    }
    static Person dates(Person p, LocalDate hire, LocalDate retire) {
        return new Person(p.personId(),p.managementNo(),p.name(),p.employeeType(),p.companyId(),p.companyName(),p.gradeCode(),p.jobCode(),
            p.positionCode(),p.position(),p.directClass(),p.personPartId(),p.personPartName(),hire,retire,p.workStatus(),p.useYn());
    }
    static IndividualProfitabilityPerson profile(IndividualProfitabilityPerson p,String id,String grade) {
        return new IndividualProfitabilityPerson(id,id,p.name(),p.companyName(),p.companyId(),p.personPartId(),p.personPartName(),p.position(),
            p.employeeType(),p.hireDate(),p.jobCode(),grade,p.metrics(),p.utilization(),p.workedMonths(),p.employmentMonths(),p.utilizedMonths(),
            p.turnkeyOnlyExcluded(),p.monthBasis(),p.completeness());
    }
    @Test void wholeContributionIsWeightedByPeopleRatherThanAveragingGradeMeans() {
        var small=rows(sources(person("FT"),List.of(pay("FT","100")),List.of(),List.of()),query()).get(0);
        var large=rows(sources(person("FT"),List.of(pay("FT","200")),List.of(),List.of()),query()).get(0);
        var codes=List.of(IndividualProfitabilityContractTest.code("JR","초급"),IndividualProfitabilityContractTest.code("SR","고급"));
        var people=List.of(profile(small,"owned-junior","JR"),profile(large,"owned-senior-one","SR"),profile(large,"owned-senior-two","SR"));
        var cells=calculation.contributions(people,List.of(IndividualProfitabilityContractTest.code("JOB","직무")),codes);
        var whole=cells.stream().filter(c->c.gradeCode().equals("ALL")).findFirst().orElseThrow();
        assertThat(whole.personCount()).isEqualTo(3);
        var expected=people.stream().map(p->n(p.metrics().profit())).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add)
            .divide(n("3"),0,java.math.RoundingMode.DOWN).toPlainString();
        assertThat(whole.averageProfit()).isEqualTo(expected).isNotEqualTo("-150");
        assertThat(cells.stream().filter(c->c.gradeCode().equals("UNCLASSIFIED")).findFirst().orElseThrow().averageProfit()).isNull();
    }
    @Test void averageCostIsSumOfTruncatedComponentsEvenWhenDividingCumulativeCostWouldDiffer() {
        var project=new Project("project","프로젝트","MO","ACT",null,LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),n("100"),n("100"),n("3"),n("5"));
        var s=annual(sources(person("FT"),List.of(),List.of(),List.of(project)),"202603",
            List.of(payAt(MONTH,"FT","101"),payAt("202602","FT","101"),payAt("202603","FT","101")),List.of(at(MONTH,"a","0.40")));
        var cumulative=rows(s,query()).get(0).metrics();var average=rows(s,query("view","MONTHLY_AVERAGE")).get(0).metrics();
        assertThat(average.totalCost()).isEqualTo("101");
        assertThat(n(average.totalCost())).isNotEqualByComparingTo(n(cumulative.totalCost()).divide(n("3"),0,java.math.RoundingMode.DOWN));
        assertThat(n(average.profit())).isEqualByComparingTo(n(average.revenue()).subtract(n(average.totalCost())));
    }
    @Test void retirementHireAndCurrentLeavePreserveHistoricalRowsAndCompanyLeavePopulation() {
        var seed=sources(person("FT"),List.of(),List.of(),List.of());
        var retired=dates(person("FT"),null,LocalDate.of(2026,1,15));
        var s=annual(sources(retired,List.of(),List.of(),List.of()),"202602",List.of(payAt(MONTH,"FL","100")),List.of(at(MONTH,"a","0.40")));
        var p=rows(s,query()).get(0);
        assertThat(p.employeeType()).isEqualTo("FL");assertThat(p.employmentMonths()).isEqualTo(1);
        assertThat(rows(s,query("employeeType","FT"))).isEmpty();assertThat(rows(s,query("employeeType","FL"))).hasSize(1);
        var previous=dates(person("FT"),null,LocalDate.of(2025,12,31));
        assertThat(rows(annual(sources(previous,List.of(),List.of(),List.of()),"202602",List.of(),List.of()),query())).isEmpty();
        var future=dates(person("FT"),LocalDate.of(2026,3,1),null);
        assertThat(rows(annual(sources(future,List.of(),List.of(),List.of()),"202602",List.of(),List.of()),query())).isEmpty();
        var leave=withStates(seed,List.of(new WorkState("actual","leave-owned","LEAVE",LocalDate.of(2026,1,1),null)));
        assertThat(rows(leave,query())).isEmpty();
        var summary=calculation.summary(leave,monthly.calculate(leave),rows(leave,query()));
        assertThat(summary.companyHeadcount()).isEqualTo(1);assertThat(summary.companyLeaveCount()).isEqualTo(1);
    }
    @Test void monthClassificationChangesNeverDuplicateNAndIndirectMonthsLeaveUtilizationDenominator() {
        var indirect=new Payroll(MONTH,"actual",n("100"),n("0"),n("0"),null,n("0"),null,null,null,null,"FT","INDRCT",null,null,null,null,null);
        var s=annual(sources(person("FT"),List.of(),List.of(),List.of(project("MO","ACT"))),"202603",
            List.of(indirect,payAt("202602","FT","0"),payAt("202603","FL","100")),List.of(at("202603","a","0.40")));
        var p=rows(s,query()).get(0);
        assertThat(p.workedMonths()).isEqualTo(2);assertThat(p.employmentMonths()).isEqualTo(2);assertThat(p.utilizedMonths()).isEqualTo(1);
        assertThat(p.metrics().personalCost()).isEqualTo("100");assertThat(p.utilization()).isEqualTo("50.0");
    }
    @Test void displayedRoundedHeadcountIsTheCompanyRateDenominatorNotHiddenUnroundedValue() {
        var s=annual(sources(person("FT"),List.of(),List.of(),List.of(project("MO","ACT"))),"202603",List.of(payAt(MONTH,"FT","100")),List.of(at(MONTH,"a","0.40")));
        var people=new ArrayList<Person>();people.add(person("FT"));
        for(int i=1;i<40;i++) people.add(dates(identity("owned-"+i,"인력"+i,null),i==39?LocalDate.of(2026,2,1):null,null));
        s=new Sources(s.period(),s.actor(),s.asOfDate(),people,s.prices(),s.states(),s.payroll(),s.assignments(),s.projectMonths(),s.projects(),
            s.projectParts(),s.turnkey(),s.storedCosts(),s.standards(),s.parts(),s.closes(),s.allowanceCodes());
        var summary=calculation.summary(s,monthly.calculate(s),rows(s,query()));
        assertThat(summary.averageHeadcount()).isEqualTo("39.7");
        var expected=n(summary.averageUtilizedHeadcount()).multiply(n("100")).divide(n("39.7"),1,java.math.RoundingMode.HALF_UP).toPlainString();
        assertThat(summary.companyUtilization()).isEqualTo(expected);
    }
    @Test void companyFreeAndMixedMoTkMembershipIsCountedOnceFromStoredSnapshotNotCurrentAssignments() {
        var s=sources(person("FT"),List.of(pay("FT","100")),List.of(),List.of());
        var mo=new StoredCost(MONTH,"actual","mo","a",n("0.40"),n("0.40"),n("0"),n("50"),n("0"),n("0"),n("0"),"ALLOC","MO",null,"N",null,null,null,null,null,null,"FREE");
        var tk=new StoredCost(MONTH,"actual","tk","b",n("0.40"),n("0"),n("50"),n("50"),n("0"),n("0"),n("0"),"ALLOC","TK",null,"N",null,null,null,null,null,null,"PAID");
        s=MonthlyCostContractTest.protectedSources(s,List.of(mo,tk),"CLOSE");
        var summary=calculation.summary(s,monthly.calculate(s),rows(s,query()));
        assertThat(summary.averageUtilizedHeadcount()).isEqualTo("1.0");assertThat(summary.averageEarnedHeadcount()).isEqualTo("0.0");
        var paidMo=new StoredCost(mo.month(),mo.personId(),mo.projectId(),mo.assignmentId(),mo.assignmentMm(),n("0.00"),mo.personalRevenue(),mo.laborAllocated(),mo.teamExpense(),mo.projectExpense(),mo.directExpense(),mo.costClass(),mo.contractType(),null,"N",null,null,null,null,null,null,"PAID");
        s=MonthlyCostContractTest.protectedSources(s,List.of(paidMo,tk),"CLOSE");
        summary=calculation.summary(s,monthly.calculate(s),rows(s,query()));
        assertThat(summary.averageEarnedHeadcount()).isEqualTo("1.0");
    }
}
