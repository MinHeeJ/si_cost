package com.sicost.progress;

import com.sicost.profitability.*;
import com.sicost.shared.SharedQueriesService;
import com.sicost.shared.SharedQueryDtos;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static com.sicost.progress.ProjectProgressDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectProgressCostSourceTest {
    final MonthlyCostService monthly=mock(MonthlyCostService.class);
    final SharedQueriesService shared=mock(SharedQueriesService.class);
    final ProjectProgressMapper mapper=mock(ProjectProgressMapper.class);
    final ProjectProgressCostSource source=new ProjectProgressCostSource(monthly,shared,mapper);
    final LocalDate today=LocalDate.of(2026,7,1);
    @BeforeEach void setup(){
        when(shared.getPeriodContext()).thenReturn(new SharedQueryDtos.PeriodContext(today,"2026-07","Asia/Seoul","2025-01",new SharedQueryDtos.MonthClose("2026-07","작업중")));
        when(mapper.additionalExpense(anyString(),anyString(),anyString())).thenReturn(new BigDecimal("7"));
        when(monthly.loadYear(anyInt())).thenAnswer(c->sources(c.getArgument(0)));
        when(monthly.calculate(any())).thenAnswer(c->{MonthlyCostDtos.Sources s=c.getArgument(0);String month=s.period().year()+"01";var row=new MonthlyCostDtos.StoredCost(month,"D-1","P-1","A-1",new BigDecimal("1.00"),new BigDecimal("0.00"),new BigDecimal("999"),new BigDecimal("10"),new BigDecimal("2"),new BigDecimal("3"),new BigDecimal("5"),"PROJ","TK",null,"N","admin",today.atStartOfDay(),null,null,"프로젝트","DEV","BILL").toRow();
            return new MonthlyCostDtos.MonthlyCostsData(s.period(),List.of(new MonthlyCostDtos.MonthlyPersonCost("D-1",month,List.of(row),null,new MonthlyCostDtos.MonthlyCostCompleteness(true,true,true,true,List.of()),"10","999","1.00",false,false)));});
    }
    ProjectRow project(LocalDate start){return new ProjectRow("P-1","프로젝트","TK","ING",start,LocalDate.of(2026,12,31),new BigDecimal("100"),start.atStartOfDay(),null,null,false);}
    MonthlyCostDtos.Sources sources(int year){
        var period=new MonthlyCostDtos.ProfitabilityPeriod(year,"202501",year+"01",year==2026?"202607":year+"12",today.atStartOfDay(ZoneId.of("Asia/Seoul")).toOffsetDateTime(),false);
        return new MonthlyCostDtos.Sources(period,"admin",today,List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
    }
    @Test void cumulativeCostReadsEveryYearAndAddsExtraExpenseOnceNotPersonalRevenueOrExpenseBreakdown(){
        var cost=source.load(project(LocalDate.of(2025,1,1)),today);
        assertThat(cost.amount()).isEqualByComparingTo("37");verify(monthly).loadYear(2025);verify(monthly).loadYear(2026);
        verify(mapper,times(1)).additionalExpense("P-1","202501","202607");assertThat(cost.completeness().estimated()).isTrue();assertThat(cost.completeness().costComplete()).isTrue();
    }
    @Test void preOperationMissingRangeIsNeverZeroCost(){
        var cost=source.load(project(LocalDate.of(2024,1,1)),today);
        assertThat(cost.amount()).isNull();assertThat(cost.completeness().costComplete()).isFalse();assertThat(cost.completeness().missingBeforeMonth()).isEqualTo("2025-01");assertThat(cost.completeness().issues()).anyMatch(i->i.code().equals("BEFORE_OPERATION"));verify(monthly,never()).loadYear(2024);
    }
    @Test void missingAllocationMarksWholeProjectUnknownAndPreservesSourceReason(){
        doAnswer(c->{MonthlyCostDtos.Sources s=c.getArgument(0);return new MonthlyCostDtos.MonthlyCostsData(s.period(),List.of(new MonthlyCostDtos.MonthlyPersonCost("D-1","202601",List.of(),null,new MonthlyCostDtos.MonthlyCostCompleteness(false,false,false,false,List.of(new MonthlyCostDtos.MonthlyCostIssue("D-1","202601","급여 누락","PAYROLL","UI-02",null))),null,null,"1.00",false,false)));}).when(monthly).calculate(any());
        var s=sources(2026);var a=new MonthlyCostDtos.Assignment("202601","D-1","P-1","A-1",new BigDecimal("1.00"),null,null,"N","DEV","BILL",null,"ACT",today,today);
        when(monthly.loadYear(2026)).thenReturn(new MonthlyCostDtos.Sources(s.period(),s.actor(),s.asOfDate(),s.people(),s.prices(),s.states(),s.payroll(),List.of(a),s.projectMonths(),s.projects(),s.projectParts(),s.turnkey(),s.storedCosts(),s.standards(),s.parts(),s.closes(),s.allowanceCodes()));
        var cost=source.load(project(LocalDate.of(2026,1,1)),today);assertThat(cost.amount()).isNull();assertThat(cost.completeness().issues()).anyMatch(i->i.message().equals("급여 누락"));assertThat(cost.completeness().issues()).anyMatch(i->i.code().equals("MISSING_COST"));
    }
}
