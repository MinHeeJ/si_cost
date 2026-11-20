package com.sicost.forecast;

import com.sicost.common.assignments.AssignmentMonthlyService.Month;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.sicost.forecast.SalesForecastSources.*;
import static com.sicost.forecast.SalesForecastFixture.*;
import static org.assertj.core.api.Assertions.*;

class SalesRevenueForecastTest {
    final SalesWorkforceProjectionService people=new SalesWorkforceProjectionService();
    final SalesRevenueForecastService service=new SalesRevenueForecastService(null,people,null,Clock.systemUTC());
    @Test void tkKeepsOriginalDenominatorOutsideSixMonthWindow() {
        var result=service.calculate(snapshot(List.of(project("P","TK","ING","2027-03-31","100","0")),List.of()),DAY);
        assertThat(result.revenueMonths()).extracting(SalesForecastDtos.RevenueMonth::projectRevenue).containsExactly("12","12","12","12","12","12");
        assertThat(result.summary().sixMonthRevenue()).isEqualTo("72");
    }
    @Test void tkRemainderBelongsToLastContractMonth() {
        var result=service.calculate(snapshot(List.of(project("P","TK","ING","2026-10-31","150","50")),List.of()),DAY);
        assertThat(result.revenueMonths()).extracting(SalesForecastDtos.RevenueMonth::projectRevenue).containsExactly("33","33","34","0","0","0");
    }
    @Test void overbilledTkNeverBecomesNegative() {
        assertThat(service.calculate(snapshot(List.of(project("P","TK","ING","2026-10-31","100","150")),List.of()),DAY).summary().sixMonthRevenue()).isEqualTo("0");
    }
    @Test void futureContractStartRestrictsTkWindowAndDenominator() {
        var original=project("P","TK","ING","2026-12-31","100","0");
        var p=new Project(original.id(),original.name(),original.type(),original.status(),LocalDate.of(2026,10,15),original.endDate(),original.amount(),original.billed(),null);
        assertThat(service.calculate(snapshot(List.of(p),List.of()),DAY).revenueMonths()).extracting(SalesForecastDtos.RevenueMonth::projectRevenue).containsExactly("0","0","33","33","34","0");
    }
    @ParameterizedTest @ValueSource(strings={"PRE","IN","LOSE","END"}) void nonIngProjectsHaveNoRevenue(String status) {
        assertThat(service.calculate(snapshot(List.of(project("P","TK",status,"2026-10-31","100","0")),List.of()),DAY).revenueSources()).isEmpty();
    }
    @Test void internalAndExpiredProjectsAreNotRevenueSources() {
        assertThat(service.calculate(snapshot(List.of(project("P","IN","ING","2026-10-31","100","0"),project("Q","TK","ING","2026-07-14","100","0")),List.of()),DAY).revenueSources()).isEmpty();
    }
    @Test void opportunityDivisionIsExactUntilFinalWonDownAndNullDurationComputesSix() {
        var result=service.calculate(snapshot(List.of(),List.of(opportunity("O","ING","202608",3,"100","99.99"),opportunity("Q","ING","202608",null,"100","50"))),DAY);
        assertThat(result.revenueMonths().get(0).opportunityRevenue()).isEqualTo("41");
        assertThat(result.revenueMonths().get(0).allWonRevenue()).isEqualTo("49");
        assertThat(result.revenueMonths().get(3).opportunityRevenue()).isEqualTo("8");
    }
    @Test void opportunityRemainderGoesToLastMonthSoTotalsMatchFormula57() {
        // 70,000,000 × 20% = 14,000,000 → 4,666,666 · 4,666,666 · 4,666,668 (끝자리는 마지막 달)
        var result=service.calculate(snapshot(List.of(),List.of(opportunity("O","ING","202608",3,"70000000","20"))),DAY);
        assertThat(result.revenueMonths().subList(0,3)).extracting(m -> m.opportunityRevenue()).containsExactly("4666666","4666666","4666668");
        assertThat(result.revenueMonths().subList(0,3)).extracting(m -> m.allWonRevenue()).containsExactly("23333333","23333333","23333334");
        assertThat(result.summary().opportunityRevenue()).isEqualTo("14000000");
        assertThat(result.summary().allWonRevenue()).isEqualTo("70000000");
    }
    /** 식-57 경계: 기간 비움(6개월)·1개월·나누어떨어짐·확률 100%. 달 합계 = 버림(예상 금액 × 확률 ÷ 100). */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource(delimiter='|', value={
        "100000000|70|   |11666666,11666666,11666666,11666666,11666666,11666670|70000000",
        "1000001  |50|1  |500000|500000",
        "60000000 |50|3  |10000000,10000000,10000000|30000000",
        "100      |100|3 |33,33,34|100"})
    void opportunityMonthsSumToFlooredWeightedAmount(String amount,String probability,Integer months,String expectedMonths,String total) {
        var result=service.calculate(snapshot(List.of(),List.of(opportunity("O","ING","202608",months,amount,probability))),DAY);
        var expected=List.of(expectedMonths.split(","));
        assertThat(result.revenueMonths().subList(0,expected.size())).extracting(m -> m.opportunityRevenue()).containsExactlyElementsOf(expected);
        assertThat(result.summary().opportunityRevenue()).isEqualTo(total);
    }
    @Test void opportunityWeightedAmountIsFlooredOnceBeforeSpreading() {
        // 100 × 99.99% = 99.99 → 버림 99 → 33 · 33 · 33
        var result=service.calculate(snapshot(List.of(),List.of(opportunity("O","ING","202608",3,"100","99.99"))),DAY);
        assertThat(result.revenueMonths().subList(0,3)).extracting(m -> m.opportunityRevenue()).containsExactly("33","33","33");
        assertThat(result.summary().opportunityRevenue()).isEqualTo("99");
    }
    @ParameterizedTest @ValueSource(strings={"WIN","LOSE","STOP"}) void closedOpportunitiesDoNotDuplicateRevenue(String status) {
        assertThat(service.calculate(snapshot(List.of(),List.of(opportunity("O",status,"202608",3,"100","100"))),DAY).revenueSources()).isEmpty();
    }
    @Test void existingProjectLinkAlsoSuppressesStaleIngOpportunity() {
        var base=project("P","TK","ING","2026-10-31","100","0");
        var linked=new Project(base.id(),base.name(),base.type(),base.status(),base.startDate(),base.endDate(),base.amount(),base.billed(),"O");
        assertThat(service.calculate(snapshot(List.of(linked),List.of(opportunity("O","ING","202608",3,"100","100"))),DAY).revenueSources()).allMatch(r -> r.sourceType().equals("PROJECT"));
    }
    @ParameterizedTest @ValueSource(strings={"MO","DP"}) void monthlyRevenueUsesPaidPreviewThroughWithdrawalWithoutExtension(String type) {
        var p=project("P",type,"ING","2026-12-31","0","0");
        var a=new Assignment(source("A","NEW","2026-08-15","H"),List.of(new Month("2026-08","P","D",15,"1.00","0.48","100","48","N")));
        var free=new Assignment(source("F","FREE","2026-08-31",null),List.of(new Month("2026-08","P","D",31,"1.00","1.00","0","999","N")));
        var s=snapshot(List.of(p),List.of());s=new Snapshot(s.projects(),s.opportunities(),s.needs(),s.plans(),s.people(),s.leaves(),s.candidates(),s.standards(),s.jobs(),List.of(a,free));
        assertThat(service.calculate(s,DAY).revenueMonths()).extracting(SalesForecastDtos.RevenueMonth::projectRevenue).containsExactly("48","0","0","0","0","0");
    }
    @Test void missingPaidPriceIsUnknownRatherThanZeroAcrossForecastAndSummary() {
        var missing=new Assignment(source("A","NEW","2026-08-31",null),List.of(new Month("2026-08","P","D",31,"1.00","1.00",null,"0","N")));
        var base=snapshot(List.of(project("P","MO","ING","2026-12-31","0","0")),List.of());
        var unknown=service.calculate(new Snapshot(base.projects(),base.opportunities(),base.needs(),base.plans(),base.people(),base.leaves(),base.candidates(),base.standards(),base.jobs(),List.of(missing)),DAY);
        assertThat(unknown.revenueMonths().get(0).projectRevenue()).isNull();assertThat(unknown.summary().sixMonthRevenue()).isNull();assertThat(unknown.revenueSources().get(0).basis()).contains("단가 미확인");
    }
    @Test void demandUsesNeedsOwnMonthsAndAverageHalfUpOne() {
        var s=workforce(List.of(new Need("O",1,"BE","202608",1,1,n("1.00"))),List.of(),List.of(),List.of(),List.of());
        var r=service.calculate(s,DAY);
        assertThat(r.workforce().totals()).extracting(SalesForecastDtos.WorkforceMonth::demand).containsExactly("0.5","0.0","0.0","0.0","0.0","0.0");
        assertThat(r.summary().averageDemand()).isEqualTo("0.1");
    }
    @Test void anyDayLeaveExcludesSupplyButJoinAndRetireMonthsRemainEligible() {
        var s=workforce(List.of(),List.of(),List.of(new Person("D","BE","Y","DRCT",LocalDate.of(2026,8,30),LocalDate.of(2026,10,1))),
            List.of(new Leave("D",LocalDate.of(2026,9,15),LocalDate.of(2026,9,15))),List.of());
        assertThat(people.calculate(s,DAY,YearMonth.of(2026,8),6,true).totals()).extracting(SalesForecastDtos.WorkforceMonth::supply).containsExactly("1.0","0.0","1.0","0.0","0.0","0.0");
    }
    @Test void extensionUsesDbProbabilityAfterEndAndClampsOverassignmentAtPersonLevel() {
        var a=new Assignment(source("A","NEW","2026-08-31","H"),List.of(new Month("2026-08","P","D",31,"1.00","1.20","100","120","N")));
        var s=workforce(List.of(),List.of(),List.of(new Person("D","BE","Y","DRCT",null,null)),List.of(),List.of(a));
        assertThat(people.calculate(s,DAY,YearMonth.of(2026,8),6,true).totals()).extracting(SalesForecastDtos.WorkforceMonth::supply).containsExactly("0.0","0.2","0.2","0.2","0.2","0.2");
    }
    @Test void missingExtensionIsNullNotZeroAndUnaffectedDemandSurvives() {
        var base=workforce(List.of(),List.of(),List.of(new Person("D","BE","Y","DRCT",null,null)),List.of(),List.of(new Assignment(source("A","NEW","2026-08-31","H"),List.of())));
        var standards=new HashMap<>(base.standards());standards.remove("EXT_PRBL_H");
        var s=new Snapshot(base.projects(),base.opportunities(),base.needs(),base.plans(),base.people(),base.leaves(),base.candidates(),standards,base.jobs(),base.assignments());
        var r=service.calculate(s,DAY);assertThat(r.summary().averageSupply()).isNull();assertThat(r.workforce().totals().get(0).supply()).isEqualTo("1.0");
        assertThat(r.workforce().totals().get(1).supply()).isNull();assertThat(r.workforce().totals().get(1).demand()).isEqualTo("0.0");
        assertThat(r.workforce().issues()).extracting(SalesForecastDtos.Issue::field).contains("EXT_PRBL_H");
    }
    @Test void unfilledUsesWholeContractGapAndOriginalRemainingMonthsWithOptionalExclusion() {
        var s=workforce(List.of(),List.of(new Plan("P","BE",n("4"))),List.of(),List.of(),List.of());
        assertThat(people.calculate(s,DAY,YearMonth.of(2026,8),6,true).totals()).extracting(SalesForecastDtos.WorkforceMonth::unfilledDemand).containsOnly("0.5");
        assertThat(people.calculate(s,DAY,YearMonth.of(2026,8),6,false).totals()).extracting(SalesForecastDtos.WorkforceMonth::demand).containsOnly("0.0");
    }
    @Test void exactShortageBoundaryIsNeutralAndDoesNotSuggestHiring() {
        var s=workforce(List.of(new Need("O",1,"BE","202608",6,1,n("0.80"))),List.of(),List.of(),List.of(),List.of());
        var result=people.calculate(s,DAY,YearMonth.of(2026,8),6,true);
        assertThat(result.totals()).extracting(SalesForecastDtos.WorkforceMonth::status).containsOnly("BALANCED");assertThat(result.hiring()).isEmpty();
        assertThat(result.maximumShortage()).isEqualTo("0.4");assertThat(result.mostShortageJobCode()).isEqualTo("BE");
    }
    @Test void hiringUsesFirstStrictShortageLeadAndCandidateCounts() {
        var s=workforce(List.of(new Need("O",1,"BE","202608",6,1,n("1.00"))),List.of(),List.of(),List.of(),List.of());
        var h=people.calculate(s,DAY,YearMonth.of(2026,8),6,true).hiring().get(0);
        assertThat(h.firstShortageMonth()).isEqualTo("2026-08");assertThat(h.recommendedStartMonth()).isEqualTo("2026-06");assertThat(h.startNow()).isTrue();assertThat(h.candidateCount()).isEqualTo(1);
    }
}
