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
}
