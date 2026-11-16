package com.sicost.automationrunsops;

import com.sicost.common.concurrent.*;
import com.sicost.common.history.ChangeHistoryWriter;
import com.sicost.common.validation.DeveloperWorkStatus;
import com.sicost.developers.DeveloperQueryService;
import com.sicost.projectsops.ProjectStatusDtos.ValueHistory;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.sicost.automationrunsops.DailyAutomationDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DailyAutomationActionsTest {
    DailyAutomationMapper mapper=mock(DailyAutomationMapper.class);
    MonthCloseBoundary boundary=mock(MonthCloseBoundary.class);
    SettlementSourceLocks locks=mock(SettlementSourceLocks.class);
    ChangeHistoryWriter history=mock(ChangeHistoryWriter.class);
    DeveloperQueryService developers=mock(DeveloperQueryService.class);
    PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    Clock clock=Clock.fixed(Instant.parse("2026-07-31T17:00:00Z"),ZoneOffset.UTC);
    DailyAutomationRepository repository;
    DailyAutomationActions actions;
    LocalDate today=LocalDate.of(2026,8,1);
    Assignment row;
    @BeforeEach void setup() {
        when(manager.getTransaction(any())).thenAnswer(i->new SimpleTransactionStatus());
        repository=new DailyAutomationRepository(mapper,boundary,locks,history,clock,manager);
        actions=new DailyAutomationActions(mapper,repository,developers,history,clock);
        row=new Assignment("A","P","D",today.minusMonths(1),today.plusMonths(2),"ACT","ING",new BigDecimal("1.00"),new BigDecimal("100"));
        when(mapper.assignmentIdentity("A")).thenReturn(new Item("A","P","D"));when(mapper.assignment("A")).thenAnswer(i->row);when(mapper.pointer(any())).thenReturn(1);
    }
    @Test void monthFirstUsesEffectiveHistoryOnlyAndAuditsOnlyPointersNotMonthlyRows() {
        when(mapper.valueHistory("A")).thenReturn(List.of(
            new ValueHistory(1,"INPUT",today.minusMonths(1),new BigDecimal("1.00"),new BigDecimal("100"),"N",null),
            new ValueHistory(2,"RATE",today,new BigDecimal("0.50"),null,"N",null),
            new ValueHistory(3,"UPRC",today.plusMonths(1),null,new BigDecimal("999"),"N",null)));
        actions.process(4,"A",today);
        verify(mapper).pointer(argThat(p->p.mm().compareTo(new BigDecimal("0.50"))==0&&p.price().compareTo(new BigDecimal("100"))==0));
        verify(history).appendAsSystem(argThat(c->c.item().equals("CTRT_MM")&&c.before().equals("1.00")&&c.after().equals("0.50")));
        verifyNoMoreInteractions(history);
        var order=inOrder(boundary,locks,mapper);order.verify(mapper).assignmentIdentity("A");order.verify(boundary).shared();order.verify(locks).lock(List.of("ASGN:A","DEV:D","PROJECT:P"));order.verify(mapper).assignment("A");
    }
    @Test void unchangedPointersAreNoOpWithoutRepeatedAudit() {
        when(mapper.valueHistory("A")).thenReturn(List.of(new ValueHistory(1,"INPUT",today.minusMonths(1),new BigDecimal("1.0"),new BigDecimal("100.00"),"N",null)));
        actions.process(4,"A",today);verify(mapper,never()).pointer(any());verifyNoInteractions(history);
    }
    @Test void protectedMonthIsNotModified() {
        when(mapper.protectedMonth("202608")).thenReturn(true);actions.process(4,"A",today);verify(mapper,never()).pointer(any());verifyNoInteractions(history);
    }
    @Test void nonFirstDayDoesNotAdvancePointers() {
        actions.process(4,"A",today.plusDays(1));verify(mapper,never()).pointer(any());verifyNoInteractions(history);
    }
    @Test void missingOriginalInputFailsInsteadOfMakingHistory() {
        when(mapper.valueHistory("A")).thenReturn(List.of(new ValueHistory(2,"RATE",today,new BigDecimal("0.5"),null,"N",null)));
        assertThatThrownBy(()->actions.process(4,"A",today)).isInstanceOf(IllegalStateException.class);verify(mapper,never()).pointer(any());verifyNoInteractions(history);verify(manager).rollback(any());
    }
    @Test void cancelledFutureAndEndedAssignmentsAreIneligible() {
        for(Assignment candidate:List.of(
            new Assignment("A","P","D",today,today.plusMonths(1),"CNCL","ING",row.mm(),row.price()),
            new Assignment("A","P","D",today.plusDays(1),today.plusMonths(1),"ACT","ING",row.mm(),row.price()),
            new Assignment("A","P","D",today.minusMonths(1),today.minusDays(1),"ACT","ING",row.mm(),row.price()),
            new Assignment("A","P","D",today.minusMonths(1),today.plusDays(1),"ACT","END",row.mm(),row.price()))) {
            row=candidate;actions.process(4,"A",today);
        }
        verify(mapper,never()).pointer(any());verifyNoInteractions(history);
    }
    @Test void developerItemUsesSharedCloseBoundaryBeforeAggregateAndCommonSystemCommand() {
        actions.process(1,"D",today);
        var order=inOrder(boundary,locks,developers);order.verify(boundary).shared();order.verify(locks).lock(List.of("DEV:D"));order.verify(developers).synchronize("D",true);
    }
    @Test void retirementIsStrictlyAfterFinalWorkingDayAndManualStopDoesNotChangeCalculation() {
        assertThat(DeveloperWorkStatus.calculate(today,List.of(),List.of(),today)).isEqualTo("IDLE");
        assertThat(DeveloperWorkStatus.calculate(today,List.of(),List.of(),today.plusDays(1))).isEqualTo("RETIRE");
        assertThat(DeveloperQueryService.calculate(Map.of("useYn","N"),List.of(),List.of(),today)).isEqualTo("IDLE");
    }
}
