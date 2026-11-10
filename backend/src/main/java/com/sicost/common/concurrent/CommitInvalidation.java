package com.sicost.common.concurrent;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class CommitInvalidation {
    public record CommittedChange(String aggregateKey) {}
    private final ApplicationEventPublisher publisher;
    public CommitInvalidation(ApplicationEventPublisher publisher) { this.publisher = publisher; }
    public void afterCommit(String key) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("활성 트랜잭션이 필요합니다.");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { publisher.publishEvent(new CommittedChange(key)); }
        });
    }
}
