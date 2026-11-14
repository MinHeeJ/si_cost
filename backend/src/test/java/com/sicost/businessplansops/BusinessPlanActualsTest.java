package com.sicost.businessplansops;

import com.sicost.profitability.*;
import com.sicost.profitability.MonthlyCostDtos.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static com.sicost.businessplansops.BusinessPlanPerformanceDtos.*;

class BusinessPlanActualsTest {
    final BusinessPlanActuals calculation=new BusinessPlanActuals();
    final OffsetDateTime at=OffsetDateTime.parse("2026-07-15T09:00:00+09:00");
    BigDecimal n(String value) {return value==null?null:new BigDecimal(value);}
    Sources sources() {
        var person=new Person("owned","owned-no","시험","FT",null,null,null,"DEV",null,null,"DRCT",null,null,null,null,"WORK","Y");
        var pay=new Payroll("202607","owned",n("1000"),n("0"),n("0"),null,n("0"),null,null,null,null,"FT","DRCT",null,null,null,null,null);
        var assignment=new Assignment("202607","owned","mo","owned-assignment",n("0.60"),n("2000"),n("1200"),"N","DEV","PAID",null,"ACT",LocalDate.of(2026,7,1),LocalDate.of(2026,7,31));
        var project=new Project("mo","회사 유상","MO","ACT",null,LocalDate.of(2026,1,1),LocalDate.of(2026,12,31),n("2000"),n("1000"),n("0"),n("0"));
        return new Sources(new ProfitabilityPeriod(2026,"202601","202607","202607",at,false),"admin",LocalDate.of(2026,7,15),
            List.of(person),List.of(),List.of(),List.of(pay),List.of(assignment),List.of(assignment),List.of(project),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
    }
    Source source(String metric,String value) {return new Source("2026-07","TB_FI_ITEM","owned",metric,value,false,null);}
    BusinessPlanActuals.OperatingSources operating(List<MonthlyPersonCost> people,List<Source> financial) {return new BusinessPlanActuals.OperatingSources(people,financial,sources());}
    @Test void usesRealWholeCompanyCostCalculationAndAddsAttributableSalesOnce() {
        var payroll=new EstimatedPayrollAdapter();var core=new MonthlyCostCalculation(payroll);
        var service=new MonthlyCostService(mock(MonthlyCostRepository.class),core,new TurnkeyRevenueCalculation(),payroll);
        var result=service.calculate(sources());
        var actual=calculation.calculated("2026-07",operating(result.items(),List.of(source("tkSales","300"),source("directExtra","10"),source("sgaInputAmt","20"),source("prevAdjAmt","-5"))),"0",at);
        assertThat(actual.amounts()).containsEntry("salesAmt","1500").containsEntry("allocCostAmt","600").containsEntry("idleCostAmt","400")
            .containsEntry("drctExpsAmt","10").containsEntry("oprtPrftAmt","465");
        assertThat(actual.completeness().revenueComplete()).isTrue();
    }
    MonthlyCostRow row(String cls,String type,String labor,String direct,String revenue) {
        return new MonthlyCostRow("owned",cls,cls,"202607","1.00","0.00",revenue,labor,"8","9",direct,null,null,cls,type,null,"N",null,"DEV","PAID",null,false,labor!=null&&direct!=null,revenue!=null,false,null,null,null,null);
    }
    MonthlyPersonCost person(List<MonthlyCostRow> rows) {return new MonthlyPersonCost("owned","202607",rows,null,new MonthlyCostCompleteness(true,false,false,true,List.of()),"0",null,"1.00",false,false);}
    @Test void categoriesIncludeIndirectIdleResearchAndDirectExpenseWithoutDoubleCounting() {
        var rows=List.of(row("ALLOC","TK","100","17",null),row("PRE","MO","20","1","0"),row("IDLE","MO","30","2","0"),row("INDRCT",null,"40","0","0"),row("RND","IN","50","3","0"));
        // No paid MO assignment is declared by this fixture: only TK billing is actual revenue.
        var original=sources();original=new Sources(original.period(),original.actor(),original.asOfDate(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        var actual=calculation.calculated("2026-07",new BusinessPlanActuals.OperatingSources(List.of(person(rows)),List.of(source("tkSales","500"),source("directExtra","7"),source("rndExtra","4")),original),null,at);
        assertThat(actual.amounts()).containsEntry("salesAmt","500").containsEntry("allocCostAmt","120").containsEntry("idleCostAmt","30").containsEntry("drctExpsAmt","27").containsEntry("indrctLaborAmt","40").containsEntry("rndAmt","57").containsEntry("oprtPrftAmt","226").containsEntry("netPrftAmt",null);
        assertThat(actual.completeness().revenueComplete()).isTrue();assertThat(actual.completeness().taxComplete()).isFalse();
    }
    @Test void missingPaidAssignmentDoesNotBecomeZeroRevenue() {
        var actual=calculation.calculated("2026-07",operating(List.of(),List.of(source("tkSales","500"))),null,at);
        assertThat(actual.amounts().get("salesAmt")).isNull();assertThat(actual.completeness().revenueComplete()).isFalse();
    }
    @Test void negativeSourcesAndRatesAreRetained() {
        assertThat(BusinessPlanActuals.ratio("-5","10",true)).isEqualTo("-50.0");
        assertThat(BusinessPlanActuals.ratio("5","0",true)).isNull();
        assertThat(BusinessPlanPerformanceService.margin("-100","10","100","-20").rate()).isEqualTo("10.0");
    }
    @Test void marginUsesUnroundedRationalDifferenceForColor() {
        var margin=BusinessPlanPerformanceService.margin("1000000","99999","1000000","100000");
        assertThat(margin.rate()).isEqualTo("0.0");assertThat(margin.adverse()).isTrue();
    }
    @Test void positiveSalesTargetAllowsZeroAndNegativeProfitTarget() {
        assertThat(BusinessPlanPerformanceService.margin("100","10","100","0").rate()).isEqualTo("10.0");
        assertThat(BusinessPlanPerformanceService.margin("100","10","100","-10").rate()).isEqualTo("20.0");
    }
    @Test void zeroActualSalesOrNonPositiveTargetMakesMarginUndefined() {
        assertThat(BusinessPlanPerformanceService.margin("0","10","100","10").rate()).isNull();
        assertThat(BusinessPlanPerformanceService.margin("100","10","0","10").adverse()).isNull();
        assertThat(BusinessPlanPerformanceService.margin("100","10","-100","10").rate()).isNull();
    }
}
