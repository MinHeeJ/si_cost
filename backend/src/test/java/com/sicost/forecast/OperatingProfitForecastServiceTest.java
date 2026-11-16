package com.sicost.forecast;

import com.sicost.businessplansops.*;
import com.sicost.profitability.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.sicost.forecast.SalesForecastFixture.*;

class OperatingProfitForecastServiceTest {
    @Test void usesOnlyProjectRevenueAndSharedCostCompositionAcrossYearBoundaryWithoutWritingPayroll(){
        Clock clock=Clock.fixed(DAY.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),ZoneId.of("Asia/Seoul"));
        var repository=mock(MonthlyCostRepository.class);var assignments=mock(SalesForecastRepository.class);var finance=mock(BusinessPlanPerformanceMapper.class);
        var p=new MonthlyCostDtos.ProfitabilityPeriod(2026,"202601","202608","202701",OffsetDateTime.now(clock),false);
        var originals=new MonthlyCostDtos.Sources(p,"admin",DAY,List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        when(repository.loadForecastRange(YearMonth.of(2026,8),YearMonth.of(2027,1))).thenReturn(originals);
        var source=snapshot(List.of(project("P","TK","ING","2027-03-31","800","0")),List.of(opportunity("O","ING","202608",6,"600","100")));
        when(assignments.load(YearMonth.of(2026,7))).thenReturn(source);
        var workforce=new SalesWorkforceProjectionService();var revenue=new SalesRevenueForecastService(null,workforce,null,clock);
        var financial=new ArrayList<BusinessPlanPerformanceDtos.Source>();
        for(int i=0;i<6;i++){String month=YearMonth.of(2026,8).plusMonths(i).toString();financial.add(new BusinessPlanPerformanceDtos.Source(month,"TB_FI_ITEM",month,"directExtra","20",false,"직접 경비"));financial.add(new BusinessPlanPerformanceDtos.Source(month,"TB_FI_ITEM",month,"sgaInputAmt","30",false,"판관비"));financial.add(new BusinessPlanPerformanceDtos.Source(month,"TB_CO_PREV_ADJ",month,"prevAdjAmt","5",false,"전월 조정"));}
        when(finance.financialSources(any())).thenReturn(financial);
        var costs=new MonthlyCostService(null,null,null,null); // No people: the real shared calculator returns an empty payroll-cost set.
        var service=new OperatingProfitForecastService(repository,costs,assignments,revenue,finance,new BusinessPlanActuals(),clock);
        var rows=service.forecast();assertThat(rows).hasSize(6);assertThat(rows.get(5).month()).isEqualTo("2027-01");
        assertThat(revenue.calculate(source,DAY).revenueMonths().get(0).totalRevenue()).isEqualTo("200");
        assertThat(rows).allSatisfy(month->{assertThat(month.projectRevenue()).isEqualTo("100");assertThat(month.salesCost()).isEqualTo("20");assertThat(month.sga()).isEqualTo("30");assertThat(month.previousAdjustment()).isEqualTo("5");assertThat(month.operatingProfit()).isEqualTo("55");});
        verify(repository,times(1)).loadForecastRange(YearMonth.of(2026,8),YearMonth.of(2027,1));verifyNoMoreInteractions(repository);
        financial.set(0,new BusinessPlanPerformanceDtos.Source("2026-08","TB_FI_ITEM","missing","directExtra",null,false,"경비 미확인"));
        assertThat(service.forecast().get(0).operatingProfit()).isNull();
    }
}
