package com.sicost.apiops;

import com.sicost.common.assignments.*;
import com.sicost.common.assignments.AssignmentMonthlyService.Source;
import com.sicost.profitability.*;
import com.sicost.profitability.MonthlyCostDtos.*;
import com.sicost.projectsops.ProjectStatusDtos.ValueHistory;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateRenegotiationRepositoryTest {
    MonthlyCostRepository costs;
    MonthlyCostService service;
    AssignmentMonthlyMapper assignments;
    RateRenegotiationMapper mapper;
    RateRenegotiationRepository repository;
    Sources sources;
    Source source;
    @BeforeEach void setup() {
        costs=mock(MonthlyCostRepository.class); service=mock(MonthlyCostService.class);
        assignments=mock(AssignmentMonthlyMapper.class); mapper=mock(RateRenegotiationMapper.class);
        var fixture=new RateRenegotiationQueryTest(); fixture.target=new BigDecimal("20");
        sources=fixture.snapshot().sources();
        var monthly = new AssignmentMonthlyService(assignments, null, null, Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        repository=new RateRenegotiationRepository(costs,service,monthly,mapper,Clock.fixed(Instant.parse("2080-07-15T00:00:00Z"),ZoneId.of("Asia/Seoul")));
        source=new Source("a","p","dev","DEV",LocalDate.of(2080,7,16),LocalDate.of(2080,8,31),new BigDecimal("2"),new BigDecimal("9000000"),"ADD",null,null,0,"N","ACT");
        when(costs.loadMonth(2080,7)).thenReturn(sources);
        when(mapper.assignments(any())).thenReturn(List.of(source));
        when(assignments.cashBase()).thenReturn("208001");
        when(assignments.histories("a")).thenReturn(List.of(
            new ValueHistory(1,"INPUT",LocalDate.of(2080,7,16),new BigDecimal("1"),new BigDecimal("6000000"),"N",null),
            new ValueHistory(2,"RATE",LocalDate.of(2080,8,1),new BigDecimal("2"),null,"N",null),
            new ValueHistory(3,"UPRC",LocalDate.of(2080,8,1),null,new BigDecimal("9000000"),"N",null)));
        when(service.calculate(any())).thenAnswer(call -> new MonthlyCostsData(((Sources)call.getArgument(0)).period(), List.of()));
    }
    @Test void reusesPreviewTypedHistoryAndPassesSameMonthToUnfilteredCostEngineWithoutSynchronizing() {
        var result=repository.load(); var row=result.candidates().get(0);
        assertThat(row.month().contractMm()).isEqualTo("1.00");
        assertThat(row.month().monthlyMm()).isEqualTo("0.52");
        assertThat(row.month().appliedUnitPrice()).isEqualTo("6000000");
        assertThat(row.month().salesAmount()).isEqualTo("3120000");
        assertThat(result.sources().assignments().get(0).assignmentMm()).isEqualByComparingTo("0.52");
        verify(service).calculate(result.sources()); verify(costs).loadMonth(2080,7); verifyNoMoreInteractions(costs,service);
        verify(assignments,never()).saveMonth(any()); verify(assignments,never()).project(any()); verify(assignments,never()).deleteMonth(any(),any());
    }
    @Test void protectedMonthUsesStoredAssignmentAndCostSnapshotAndDoesNotRecalculateStoredAmounts() {
        when(assignments.closeStatus(any())).thenReturn("CLOSE");
        when(assignments.months("a")).thenReturn(List.of(new AssignmentMonthlyService.StoredMonth("208007",16,new BigDecimal("0.40"),new BigDecimal("5000000"),new BigDecimal("2000000"),"Y")));
        var result=repository.load();
        assertThat(result.candidates().get(0).month().monthlyMm()).isEqualTo("0.40");
        assertThat(result.candidates().get(0).month().appliedUnitPrice()).isEqualTo("5000000");
        assertThat(result.candidates().get(0).month().salesAmount()).isEqualTo("2000000");
        assertThat(result.sources().storedCosts()).isEqualTo(sources.storedCosts());
        verify(assignments,never()).saveMonth(any());
    }
    @Test void missingProtectedAssignmentSnapshotRemainsUnevaluatedEvidenceRatherThanSynthesizedRevenue() {
        when(assignments.closeStatus(any())).thenReturn("CLOSE");
        var result=repository.load();
        assertThat(result.candidates()).hasSize(1);
        assertThat(result.candidates().get(0).month().salesAmount()).isNull();
        assertThat(result.sources().assignments()).isEmpty();
    }
}
