package com.sicost.structure;

import com.sicost.businessplansops.BusinessPlanActuals;
import com.sicost.businessplansops.BusinessPlanPerformanceDtos.*;
import com.sicost.profitability.MonthlyCostDtos.*;
import com.sicost.profitability.TurnkeyRevenueCalculation;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.sicost.structure.ProfitStructureRepository.*;
import static com.sicost.structure.ProfitStructureDtos.*;
import com.sicost.structure.ProfitStructureDtos.Query;
import static org.assertj.core.api.Assertions.*;

class ProfitStructureCalculationTest {
    final OffsetDateTime at=OffsetDateTime.parse("2026-07-15T09:00:00+09:00");
    final BusinessPlanActuals actuals=new BusinessPlanActuals();
    final RevenueCompositionCalculation calculation=new RevenueCompositionCalculation(new TurnkeyRevenueCalculation());
    BigDecimal n(String value) {return value==null?null:new BigDecimal(value);}
    Actual month(String month,String sales,String profit) {
        return actuals.stored(new Snapshot(month.replace("-",""),"Y","Y",n(sales),n("10"),n("5"),n("5"),n("20"),n(profit),n("0"),n("0"),n("0"),n("0"),n("0"),n(profit),n("0"),n("0"),n("0"),n(profit)),true,null,at);
    }
    Sources sources(List<Project> projects,List<TurnkeyCell> cells) {
        return new Sources(new ProfitabilityPeriod(2026,"202601","202601","202607",at,false),"admin",LocalDate.of(2026,7,15),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),projects,List.of(),cells,List.of(),List.of(),List.of(),List.of(),List.of());
    }
    MonthlyCostRow row(String id,String type,String cls,String sales,String labor,String direct) {
        return new StoredCost("202607","person",id,"assignment",n("0.5"),n("0"),n(sales),n(labor),n("2"),n("3"),n(direct),cls,type,null,"Y",null,null,null,null,id,"DEV","PAID").toRow();
    }
    MonthlyPersonCost person(List<MonthlyCostRow> rows) {return new MonthlyPersonCost("person","202607",rows,null,new MonthlyCostCompleteness(true,true,false,true,List.of()),"100","100","0.50",false,false);}
    Project project(String id,String budget) {return new Project(id,id,"TK","ACT",null,LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),n("1000"),n(budget),n("0"),n("0"));}
    Read read(Sources sources,List<MonthlyCostRow> rows,List<ProjectAmount> expenses) {
        Actual actual=actuals.calculated("2026-07",new BusinessPlanActuals.OperatingSources(List.of(person(rows)),List.of(),sources),"0",at);
        return new Read(new Context(2026,"2026-07","2026-01","2026-07","Asia/Seoul",at,"MONTH","WON",true),List.of(actual),Map.of(actual.month(),actual),Map.of(2026,new Details(sources,List.of(person(rows)),List.of(),expenses)),List.of(),List.of());
    }
    @Test void companyRevenueExcludesPersonalTkReferencesAndCostsPreserveAllClassifications() {
        var rows=List.of(row("mo","MO","ALLOC","100","30","5"),row("dp","DP","ALLOC","200","40","6"),row("tk","TK","ALLOC","999999","50","7"),row("idle","TK","IDLE","0","20","8"),row("indirect","IN","INDRCT","0","10","0"),row("rnd","IN","RND","0","15","9"));
        var finance=List.of(new Source("2026-07","TB_BL_BILL","bill","tkSales","500",false,null),new Source("2026-07","TB_PJ_DRCT_EXPS","extra","directExtra","11",false,null),new Source("2026-07","TB_PJ_DRCT_EXPS","rnd","rndExtra","12",false,null),new Source("2026-07","TB_CO_PREV_ADJ","adj","prevAdjAmt","-5",false,null));
        var actual=actuals.calculated("2026-07",new BusinessPlanActuals.OperatingSources(List.of(person(rows)),finance,sources(List.of(),List.of())),"-2",at);
        assertThat(actual.amounts()).containsEntry("salesAmt","800").containsEntry("allocCostAmt","120").containsEntry("idleCostAmt","20").containsEntry("drctExpsAmt","37").containsEntry("indrctLaborAmt","10").containsEntry("rndAmt","36").containsEntry("oprtPrftAmt","572").containsEntry("netPrftAmt","574");
        assertThat(actual.completeness().revenueComplete()).isTrue();
    }
    @Test void referenceUsesSelectedPlanIncludingExplicitZeroAndItemWithoutAffectingActual() {
        var p=project("tk","100");var cells=List.of(new TurnkeyCell("tk","EST","1","202607","ITEM",null,"estimate",n("999")),new TurnkeyCell("tk","PLAN","1","202607","DEV","person",null,n("0")),new TurnkeyCell("tk","PLAN","2","202607","ITEM",null,"item",n("7")));
        var read=read(sources(List.of(p),cells),List.of(row("tk","TK","ALLOC","888","10","5")),List.of());
        assertThat(calculation.reference(read,read.months()).amount()).isEqualTo("7");assertThat(read.months().get(0).amounts().get("salesAmt")).isEqualTo("0");
    }
    @Test void fallbackIncludesFullMonthlyCostAndExtraOnceAndRejectsNonPositiveBudget() {
        var read=read(sources(List.of(project("tk","100")),List.of()),List.of(row("tk","TK","ALLOC","999","10","5")),List.of(new ProjectAmount("2026-07","tk",n("3"))));
        assertThat(calculation.reference(read,read.months()).amount()).isEqualTo("180");
        var invalid=read(sources(List.of(project("tk","0")),List.of()),List.of(row("tk","TK","ALLOC",null,"10","5")),List.of());assertThat(calculation.reference(invalid,invalid.months()).amount()).isNull();assertThat(calculation.reference(invalid,invalid.months()).issues()).hasSize(1);
    }
    @Test void periodRatesUseRatioOfSumsAndNegativeValuesRemainNegative() {
        var values=ProfitStructureService.aggregate(List.of(month("2026-01","100","50"),month("2026-02","900","-90")));
        assertThat(values).containsEntry("grossPrftAmt","-40").containsEntry("grossProfitRate","-4.0");
    }
    @Test void partialPreviousYearNeverComparesDifferentMonthSets() {
        Actual a=month("2026-01","100","1"),b=month("2026-02","200","2"),previous=month("2025-01","100","1");
        var comparison=ProfitStructureService.compare("salesAmt",List.of(a.month(),b.month()),List.of("2025-01","2025-02"),Map.of(a.month(),a,b.month(),b,previous.month(),previous));
        assertThat(comparison.comparisonValue()).isEqualTo("100");assertThat(comparison.comparable()).isFalse();assertThat(comparison.changeRate()).isNull();assertThat(comparison.missingMonths()).containsExactly("2025-02");
    }
    @Test void amountChangeUsesAbsoluteNegativeDenominatorAndRatesUsePercentagePoints() {
        Actual a=month("2026-01","100","-10"),b=month("2025-01","100","-20");var all=Map.of(a.month(),a,b.month(),b);
        var amount=ProfitStructureService.compare("oprtPrftAmt",List.of(a.month()),List.of(b.month()),all);assertThat(amount.changeRate()).isEqualTo("50.0");assertThat(amount.direction()).isEqualTo("FAVORABLE");
        assertThat(ProfitStructureService.compare("operatingProfitRate",List.of(a.month()),List.of(b.month()),all).percentagePointChange()).isEqualTo("10.0");
    }
    @Test void customerTopFiveIsStableAndOtherRecomputesContributionRatherThanAverage() {
        List<RevenueCompositionCalculation.Entry> entries=new ArrayList<>();
        for(int i=0;i<7;i++)entries.add(new RevenueCompositionCalculation.Entry("2026-07","p"+i,"MO","c"+i,"c"+i,null,"미지정",Integer.toString(100-i),Integer.toString(i)));
        var top=calculation.topCustomers(entries,List.of("2026-07"));assertThat(top).containsExactly("c0","c1","c2","c3","c4");
        var actual=actuals.calculated("2026-07",new BusinessPlanActuals.OperatingSources(List.of(),List.of(new Source("2026-07","TB_BL_BILL","bill","tkSales","679",false,null)),sources(List.of(),List.of())),"0",at);
        var composition=calculation.composition(entries,List.of(actual),Map.of("salesAmt","679"),top);assertThat(composition.complete()).isTrue();
        var other=composition.customers().stream().filter(Group::other).findFirst().orElseThrow();assertThat(other.salesAmt()).isEqualTo("189");assertThat(other.allocatedLaborCostAmt()).isEqualTo("11");assertThat(other.contributionRate()).isEqualTo("94.2");
    }
    @Test void quarterAndCumulativeMonthSetsNeverIncludeFutureMonths() {
        assertThat(ProfitStructureService.bucketMonths(new Query(2026,"2026-07","QUARTER","WON"))).containsExactly(List.of("2026-01","2026-02","2026-03"),List.of("2026-04","2026-05","2026-06"),List.of("2026-07"));
        assertThat(ProfitStructureService.bucketMonths(new Query(2026,"2026-03","YTD","WON")).get(2)).containsExactly("2026-01","2026-02","2026-03");
    }
}
