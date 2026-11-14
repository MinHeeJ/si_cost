package com.sicost.common.concurrent;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SettlementSourceLocksTest {
    final SettlementSourceLockMapper mapper = mock(SettlementSourceLockMapper.class);
    final AggregateLockMapper advisory = mock(AggregateLockMapper.class);
    final SettlementSourceLocks locks = new SettlementSourceLocks(mapper, advisory);
    @BeforeEach void setup() { when(mapper.partIds(any())).thenReturn(List.of("PART-002", "PART-001", "PART-001")); }
    @Test void physicalPartHeaderThenStandardsPrecedeSortedExistingAdvisoryKeysAndRecheck() {
        var keys = List.of("PROJECT:P-001", "DEV:D-001", "ASGN:A-001", "PAY:202607", "operating-standards:CASH_BASE_YM", "DEV:D-001");
        locks.lock(keys);
        var order = inOrder(mapper, advisory);
        order.verify(mapper).partIds(argThat(s -> s.developerIds().equals(List.of("D-001"))
            && s.assignmentIds().equals(List.of("A-001")) && s.projectIds().equals(List.of("P-001")) && s.payrollMonths().equals(List.of("202607"))));
        order.verify(mapper).lockParts(List.of("PART-001", "PART-002"));
        order.verify(mapper).lockHeaders(List.of("PART-001", "PART-002"));
        order.verify(mapper).lockStandards(List.of("PART-001", "PART-002"));
        order.verify(advisory).lock("ASGN:A-001"); order.verify(advisory).lock("DEV:D-001");
        order.verify(advisory).lock("PAY:202607"); order.verify(advisory).lock("PROJECT:P-001");
        order.verify(advisory).lock("operating-standards:CASH_BASE_YM"); order.verify(mapper).partIds(any());
        verifyNoMoreInteractions(mapper, advisory);
    }
    @Test void changedMembershipConflictsAfterSourceLockAndBeforeMutation() {
        when(mapper.partIds(any())).thenReturn(List.of("PART-001"), List.of("PART-002"));
        assertThatThrownBy(() -> locks.lock(List.of("DEV:D-001"))).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.status()).isEqualTo(409); assertThat(e.code()).isEqualTo("STATE_CONFLICT");
        });
    }
    @Test void explicitOldAndNewPartKeysAreIncludedForMembershipChanges() {
        locks.lock(List.of("PART:PART-001", "PART:PART-002", "DEV:D-001"));
        verify(mapper, times(2)).partIds(argThat(s -> s.partIds().equals(List.of("PART-001", "PART-002"))));
    }
    @Test void unrelatedAggregatesKeepTheirNamespaceWithoutSettlementReads() {
        locks.lock(List.of("code-group:A", "code-group:A"));
        verifyNoInteractions(mapper); verify(advisory).lock("code-group:A");
    }
    @Test void missingSourcePopulationNeverCreatesOrLocksFakePart() {
        when(mapper.partIds(any())).thenReturn(List.of());
        locks.lock(List.of("DEV:missing"));
        verify(mapper, times(2)).partIds(any()); verifyNoMoreInteractions(mapper);
        verify(advisory).lock("DEV:missing");
    }
    @Test void coordinatorUsesSharedPathBeforeRevisionAndStaleWinsOverDomainOrNoOp() {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        var revision = new RevisionService(JsonMapper.builder().findAndAddModules().build());
        var invalidation = mock(CommitInvalidation.class);
        var shared = mock(SettlementSourceLocks.class);
        var coordinator = new WriteCoordinator(manager, advisory, revision, invalidation, shared);
        Supplier<Object> snapshot = mock(Supplier.class); when(snapshot.get()).thenReturn(Map.of("value", "latest"));
        Runnable domain = mock(Runnable.class); Runnable write = mock(Runnable.class);
        var keys = List.of("DEV:D-001");
        assertThatThrownBy(() -> coordinator.write(keys, "stale", snapshot, domain, () -> false, write)).isInstanceOf(ApiException.class);
        var order = inOrder(manager, shared, snapshot);
        order.verify(manager).getTransaction(any()); order.verify(shared).lock(keys); order.verify(snapshot).get();
        order.verify(manager).rollback(any()); verifyNoInteractions(domain, write, invalidation);
    }
    @Test void coordinatorRollsBackMembershipConflictBeforeSnapshotOrAudit() {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(mapper.partIds(any())).thenReturn(List.of("PART-001"), List.of("PART-002"));
        var invalidation = mock(CommitInvalidation.class);
        var coordinator = new WriteCoordinator(manager, advisory, new RevisionService(JsonMapper.builder().build()), invalidation, locks);
        Runnable write = mock(Runnable.class);
        assertThatThrownBy(() -> coordinator.create(List.of("DEV:D-001"), () -> "stored", () -> {}, write)).isInstanceOf(ApiException.class);
        verify(manager).rollback(any()); verifyNoInteractions(write, invalidation);
    }
}
