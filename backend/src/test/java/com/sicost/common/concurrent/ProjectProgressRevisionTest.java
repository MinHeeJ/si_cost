package com.sicost.common.concurrent;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sicost.common.api.ApiException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectProgressRevisionTest {
    final RevisionService revisions = new RevisionService(JsonMapper.builder().findAndAddModules().build());
    final ProjectProgressRevisionMapper mapper = mock(ProjectProgressRevisionMapper.class);
    final ProjectProgressAggregateRevision aggregate = new ProjectProgressAggregateRevision(mapper, revisions);

    @Test void sameSnapshotHasSameTokenAsWriteCoordinatorWithoutDoubleHashing() {
        when(mapper.snapshot("P-1")).thenReturn("stored raw aggregate");
        assertThat(aggregate.snapshot("P-1")).isEqualTo("stored raw aggregate");
        assertThat(aggregate.revision("P-1")).isEqualTo(revisions.revision(aggregate.snapshot("P-1")));
        aggregate.requireExpected(aggregate.revision("P-1"), aggregate.revision("P-1"));
    }
    @Test void absentParentIsNotAnEmptySuccessfulRevision() {
        assertThatThrownBy(() -> aggregate.snapshot("missing")).isInstanceOfSatisfying(ApiException.class,
            e -> { assertThat(e.status()).isEqualTo(404); assertThat(e.code()).isEqualTo("NOT_FOUND"); });
    }
    @Test void staleChildCreateFailsBeforeValidationOrNoOpAndRollsBack() {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(c -> new SimpleTransactionStatus());
        var locks = mock(AggregateLockMapper.class);
        var invalidation = mock(CommitInvalidation.class);
        var coordinator = new WriteCoordinator(manager, locks, revisions, invalidation);
        Runnable validate = mock(Runnable.class), write = mock(Runnable.class);
        when(mapper.snapshot("P-1")).thenReturn("new memo already committed");
        assertThatThrownBy(() -> coordinator.write(List.of("PROJECT:P-1"), revisions.revision("old snapshot"),
            () -> aggregate.snapshot("P-1"), validate, () -> false, write)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.code()).isEqualTo("STALE_WRITE"));
        verify(locks).lock("PROJECT:P-1"); verify(manager).rollback(any());
        verifyNoInteractions(validate, write, invalidation);
    }
    @Test void newChildUsesExistingWriteAndInvalidatesOnlyAfterActualChange() {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(c -> new SimpleTransactionStatus());
        var locks = mock(AggregateLockMapper.class);
        var invalidation = mock(CommitInvalidation.class);
        var coordinator = new WriteCoordinator(manager, locks, revisions, invalidation);
        var stored = new AtomicReference<>("no children");
        when(mapper.snapshot("P-1")).thenAnswer(c -> stored.get());
        var result = coordinator.write(List.of("PROJECT:P-1"), aggregate.revision("P-1"),
            () -> aggregate.snapshot("P-1"), () -> {}, () -> true, () -> stored.set("new memo and audit"));
        assertThat(result.changed()).isTrue(); assertThat(result.revision()).isEqualTo(aggregate.revision("P-1"));
        verify(invalidation).afterCommit("PROJECT:P-1"); verify(manager).commit(any());
    }
}
