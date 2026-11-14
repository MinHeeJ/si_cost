package com.sicost.common.concurrent;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WriteCoordinatorContextTest {
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final AggregateLockMapper advisory = mock(AggregateLockMapper.class);
    private final RevisionService revisions = mock(RevisionService.class);
    private final CommitInvalidation invalidation = mock(CommitInvalidation.class);
    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withBean(PlatformTransactionManager.class, () -> manager)
        .withBean(AggregateLockMapper.class, () -> advisory)
        .withBean(RevisionService.class, () -> revisions)
        .withBean(CommitInvalidation.class, () -> invalidation)
        .withBean(WriteCoordinator.class);

    @Test void isolatedContextUsesAdvisoryDoubleWithoutDatabaseDependencies() {
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(revisions.revision("stored")).thenReturn("revision");
        context.run(application -> {
            assertThat(application).hasNotFailed().hasSingleBean(WriteCoordinator.class);
            var result = application.getBean(WriteCoordinator.class)
                .create(List.of("PROJECT:P-001"), () -> "stored", () -> {}, () -> {});
            assertThat(result.changed()).isTrue();
            verify(advisory).lock("PROJECT:P-001");
            verify(manager).commit(any());
        });
    }

    @Test void availableSettlementLocksRemainTheRuntimeWritePath() {
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        var settlement = mock(SettlementSourceLocks.class);
        context.withBean(SettlementSourceLocks.class, () -> settlement).run(application -> {
            assertThat(application).hasNotFailed().hasSingleBean(WriteCoordinator.class);
            application.getBean(WriteCoordinator.class)
                .create(List.of("DEV:D-001"), () -> "stored", () -> {}, () -> {});
            verify(settlement).lock(List.of("DEV:D-001"));
            verifyNoInteractions(advisory);
            verify(manager).commit(any());
        });
    }
}
