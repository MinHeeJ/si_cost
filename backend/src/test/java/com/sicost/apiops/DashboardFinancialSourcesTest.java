package com.sicost.apiops;

import com.sicost.profitability.MonthlyCostDtos.*;
import com.sicost.structure.*;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardFinancialSourcesTest {
    @Test void historicalPayrollClassificationOverridesTodaysMasterAndLeaveStaysInDenominatorButNotIdle() {
        var sources=mock(Sources.class);var assigned=mock(Person.class);var leave=mock(Person.class);var departed=mock(Person.class);
        when(assigned.personId()).thenReturn("assigned");when(assigned.employed("202607")).thenReturn(true);when(assigned.employeeType()).thenReturn("FT");when(assigned.directClass()).thenReturn("INDRCT");
        when(leave.personId()).thenReturn("leave");when(leave.employed("202607")).thenReturn(true);when(leave.employeeType()).thenReturn("CT");when(leave.directClass()).thenReturn("DRCT");
        when(departed.employed("202607")).thenReturn(false);
        var payroll=mock(Payroll.class);when(payroll.month()).thenReturn("202607");when(payroll.personId()).thenReturn("assigned");when(payroll.employeeType()).thenReturn("FL");when(payroll.directClass()).thenReturn("DRCT");
        when(sources.people()).thenReturn(List.of(assigned,leave,departed));when(sources.payroll()).thenReturn(List.of(payroll));
        when(sources.states()).thenReturn(List.of(new WorkState("leave","1","LEAVE",java.time.LocalDate.of(2026,7,1),java.time.LocalDate.of(2026,7,31))));
        var cost=mock(MonthlyPersonCost.class);when(cost.personId()).thenReturn("assigned");when(cost.month()).thenReturn("202607");when(cost.assignmentMm()).thenReturn("1.00");
        var detail=new ProfitStructureRepository.Details(sources,List.of(cost),List.of(),List.of());
        var read=new ProfitStructureRepository.Read(null,List.of(),Map.of(),Map.of(2026,detail),List.of(),List.of());
        var result=new DashboardFinancialSources(mock(ProfitStructureRepository.class),mock(RevenueCompositionCalculation.class)).utilization(read,YearMonth.of(2026,7));
        assertThat(result.value()).isEqualTo("50.0");assertThat(result.idle()).isZero();
    }
    @Test void noDirectEmployeesDoesNotTurnTheUtilizationDenominatorIntoOne() {
        var sources=mock(Sources.class);when(sources.people()).thenReturn(List.of());
        var detail=new ProfitStructureRepository.Details(sources,List.of(),List.of(),List.of());
        var read=new ProfitStructureRepository.Read(null,List.of(),Map.of(),Map.of(2026,detail),List.of(),List.of());
        assertThat(new DashboardFinancialSources(mock(ProfitStructureRepository.class),mock(RevenueCompositionCalculation.class)).utilization(read,YearMonth.of(2026,7)).value()).isNull();
    }
}
