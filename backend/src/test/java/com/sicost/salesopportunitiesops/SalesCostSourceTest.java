package com.sicost.salesopportunitiesops;

import com.sicost.profitability.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.sicost.profitability.MonthlyCostDtos.*;

class SalesCostSourceTest {
    final SalesCommandMapper mapper=mock(SalesCommandMapper.class);
    final MonthlyCostRepository costs=mock(MonthlyCostRepository.class);
    final MonthlyCostService calculation=mock(MonthlyCostService.class);
    final SalesCommandRepository repository=new SalesCommandRepository(mapper,costs,calculation);
    Person person(String id){var p=mock(Person.class);when(p.personId()).thenReturn(id);when(p.jobCode()).thenReturn("BE");when(p.gradeCode()).thenReturn("MD");return p;}
    Payroll pay(String id){var p=mock(Payroll.class);when(p.personId()).thenReturn(id);when(p.month()).thenReturn("202606");return p;}
    MonthlyCostRow row(String labor,String team,String expense){var r=mock(MonthlyCostRow.class);when(r.laborAllocated()).thenReturn(labor);when(r.teamExpense()).thenReturn(team);when(r.projectExpense()).thenReturn(expense);when(r.directExpense()).thenReturn("999999999");return r;}
    MonthlyPersonCost monthly(String id,boolean complete,List<MonthlyCostRow> rows){return new MonthlyPersonCost(id,"202606",rows,null,new MonthlyCostCompleteness(complete,true,false,complete,List.of()),null,null,"1.00",false,false);}
    @Test void oneLatestPayrollMonthDistinctPeopleSumAllAllocationsExcludePersonalLeaveAndUnknown(){
        when(mapper.latestPayrollMonth("202607")).thenReturn("202606");when(mapper.standards(anyString())).thenReturn(List.of());when(mapper.codes()).thenReturn(List.of());
        Sources source=mock(Sources.class);when(costs.loadMonth(2026,6)).thenReturn(source);
        var people=List.of(person("a"),person("b"),person("c"),person("no-pay"));
        var payroll=List.of(pay("a"),pay("b"),pay("c"));
        when(source.people()).thenReturn(people);
        when(source.payroll()).thenReturn(payroll);
        when(source.states()).thenReturn(List.of(new WorkState("b","1","LEAVE",LocalDate.of(2026,6,15),LocalDate.of(2026,6,15))));
        var results=new MonthlyCostsData(null,List.of(
            monthly("a",true,List.of(row("3000000","10000","20000"),row("2000000","30000","40000"))),
            monthly("b",true,List.of(row("6000000","0","0"))),monthly("c",false,List.of(row(null,"0","0"))),
            monthly("no-pay",true,List.of(row("9000000","0","0")))));
        when(calculation.calculate(source)).thenReturn(results);
        var result=repository.quoteSources(YearMonth.of(2026,7));assertThat(result.costMonth()).isEqualTo("2026-06");assertThat(result.people()).hasSize(1);
        assertThat(result.people().get(0).id()).isEqualTo("a");assertThat(result.people().get(0).cost()).isEqualByComparingTo("5100000");
        verify(costs).loadMonth(2026,6);verify(costs,never()).loadYear(any());verify(calculation,never()).loadYear(any());
    }
    @Test void absentPayrollNeverLoadsOrCreatesCost(){when(mapper.standards(anyString())).thenReturn(List.of());when(mapper.codes()).thenReturn(List.of());var result=repository.quoteSources(YearMonth.of(2026,7));assertThat(result.costMonth()).isNull();assertThat(result.people()).isEmpty();verifyNoInteractions(costs,calculation);}
}
