package com.sicost.common.concurrent;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WriteCoordinatorTest {
    PlatformTransactionManager manager;
    AggregateLockMapper locks;
    CommitInvalidation invalidation;
    RevisionService revisions = new RevisionService(JsonMapper.builder().findAndAddModules().build());
    WriteCoordinator coordinator;
    @BeforeEach void setUp() {
        manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        locks = mock(AggregateLockMapper.class); invalidation = mock(CommitInvalidation.class);
        coordinator = new WriteCoordinator(manager,locks,revisions,invalidation);
    }
    @Test void locksKeysAscendingBeforeReadingAndInvalidatesOnlyChangedWrites() {
        var value = new AtomicReference<>("before");
        String expected = revisions.revision(Map.of("value",value.get()));
        var result = coordinator.write(List.of("B","A","B"),expected,() -> Map.of("value",value.get()),() -> {},() -> true,() -> value.set("after"));
        assertThat(result.changed()).isTrue();
        var order = inOrder(locks,manager,invalidation);
        order.verify(manager).getTransaction(any()); order.verify(locks).lock("A"); order.verify(locks).lock("B");
        order.verify(invalidation).afterCommit("A"); order.verify(invalidation).afterCommit("B"); order.verify(manager).commit(any());
    }
    @Test void noOpNeverRunsDataOrHistoryWork() {
        Runnable writes = mock(Runnable.class);
        var result = coordinator.write(List.of("A"),revisions.revision("same"),() -> "same",() -> {},() -> false,writes);
        assertThat(result.changed()).isFalse(); verifyNoInteractions(writes,invalidation); verify(manager).commit(any());
    }
    @Test void staleWriteRollsBackBeforeDataAndHistory() {
        Runnable writes = mock(Runnable.class);
        assertThatThrownBy(() -> coordinator.write(List.of("A"),"stale",() -> "latest",() -> {},() -> true,writes)).isInstanceOf(ApiException.class);
        verifyNoInteractions(writes,invalidation); verify(manager).rollback(any());
    }
    @Test void failureRollsBackAndDoesNotInvalidate() {
        assertThatThrownBy(() -> coordinator.write(List.of("A"),revisions.revision("same"),() -> "same",() -> {},() -> true,() -> { throw new IllegalStateException("failure"); })).isInstanceOf(IllegalStateException.class);
        verify(manager).rollback(any()); verifyNoInteractions(invalidation);
    }
    @Test void createLocksBeforeValidationAndReadsFinalSnapshotWithoutPriorRevision() {
        Runnable validation = mock(Runnable.class);
        Runnable writes = mock(Runnable.class);
        java.util.function.Supplier<String> snapshot = mock(java.util.function.Supplier.class);
        when(snapshot.get()).thenReturn("stored");
        var result = coordinator.create(List.of("USER:u", "PROJECT:P", "PROJECT:P"), snapshot, validation, writes);
        assertThat(result.changed()).isTrue();
        assertThat(result.revision()).isEqualTo(revisions.revision("stored"));
        var order = inOrder(manager, locks, validation, writes, snapshot, invalidation);
        order.verify(manager).getTransaction(any());
        order.verify(locks).lock("PROJECT:P"); order.verify(locks).lock("USER:u");
        order.verify(validation).run(); order.verify(writes).run(); order.verify(snapshot).get();
        order.verify(invalidation).afterCommit("PROJECT:P"); order.verify(invalidation).afterCommit("USER:u");
        order.verify(manager).commit(any());
    }
    @Test void failedCreateValidationDoesNotWriteOrInvalidate() {
        Runnable writes = mock(Runnable.class);
        assertThatThrownBy(() -> coordinator.create(List.of("PROJECT:P"), () -> "stored",
                () -> { throw new ApiException(409,"DUPLICATE_PROJECT","중복입니다."); }, writes)).isInstanceOf(ApiException.class);
        verify(manager).rollback(any()); verifyNoInteractions(writes,invalidation);
    }
    @Test void failedCreateHistoryRollsBackAndNeverInvalidates() {
        assertThatThrownBy(() -> coordinator.create(List.of("PROJECT:P"), () -> "stored", () -> {},
                () -> { throw new IllegalStateException("history"); })).isInstanceOf(IllegalStateException.class);
        verify(manager).rollback(any()); verifyNoInteractions(invalidation);
    }
    @Test void missingCreatedSnapshotRollsBack() {
        assertThatThrownBy(() -> coordinator.create(List.of("PROJECT:P"), () -> null, () -> {}, () -> {})).isInstanceOf(IllegalStateException.class);
        verify(manager).rollback(any()); verifyNoInteractions(invalidation);
    }
    @Test void emptyCreateLockSetIsRejectedBeforeTransaction() {
        assertThatThrownBy(() -> coordinator.create(List.of(), () -> "stored", () -> {}, () -> {})).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(manager,locks,invalidation);
    }
    @Test void existingWriteStillRejectsMissingRevisionAndUnpersistedChange() {
        assertThatThrownBy(() -> coordinator.write(List.of("PROJECT:P"),null,() -> "same",() -> {},() -> true,() -> {})).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> coordinator.write(List.of("PROJECT:P"),revisions.revision("same"),() -> "same",() -> {},() -> true,() -> {})).isInstanceOf(IllegalStateException.class);
        verify(manager,times(2)).rollback(any()); verifyNoInteractions(invalidation);
    }
}
