package com.sicost.common.concurrent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommitInvalidationTest {
    @AfterEach void cleanup() { TransactionSynchronizationManager.clear(); }
    @Test void eventIsPublishedOnlyAfterCommit() {
        var publisher = mock(ApplicationEventPublisher.class);
        var invalidation = new CommitInvalidation(publisher);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        invalidation.afterCommit("group:A");
        verifyNoInteractions(publisher);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(publisher).publishEvent(new CommitInvalidation.CommittedChange("group:A"));
    }
    @Test void rollbackDoesNotPublish() {
        var publisher = mock(ApplicationEventPublisher.class);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        new CommitInvalidation(publisher).afterCommit("group:A");
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(publisher);
    }
    @Test void nonTransactionalInvalidationIsRefused() {
        assertThatThrownBy(() -> new CommitInvalidation(mock(ApplicationEventPublisher.class)).afterCommit("A")).isInstanceOf(IllegalStateException.class);
    }
}
