package com.sicost.common.concurrent;

import java.util.Collection;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WriteCoordinator {
    private final TransactionTemplate transactions;
    private final AggregateLockMapper locks;
    private final RevisionService revisions;
    private final CommitInvalidation invalidation;
    private final SettlementSourceLocks sourceLocks;
    public WriteCoordinator(PlatformTransactionManager manager, AggregateLockMapper locks,
                            RevisionService revisions, CommitInvalidation invalidation) {
        this(manager, locks, revisions, invalidation, null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public WriteCoordinator(PlatformTransactionManager manager, AggregateLockMapper locks,
                            RevisionService revisions, CommitInvalidation invalidation, @org.springframework.lang.Nullable SettlementSourceLocks sourceLocks) {
        this.transactions = new TransactionTemplate(manager);
        this.locks = locks;
        this.revisions = revisions;
        this.invalidation = invalidation;
        this.sourceLocks = sourceLocks;
    }
    public record WriteResult(boolean changed, String revision, String message) {}

    public WriteResult create(Collection<String> aggregateKeys, Supplier<?> storedSnapshot,
                              Runnable validateDomain, Runnable dataAndHistory) {
        var keys = aggregateKeys.stream().distinct().sorted().toList();
        if (keys.isEmpty()) throw new IllegalArgumentException("잠금 대상이 필요합니다.");
        return transactions.execute(status -> {
            lock(keys);
            validateDomain.run();
            dataAndHistory.run();
            Object snapshot = storedSnapshot.get();
            if (snapshot == null) throw new IllegalStateException("생성된 집합을 조회하지 못했습니다.");
            String revision = revisions.revision(snapshot);
            keys.forEach(invalidation::afterCommit);
            return new WriteResult(true, revision, null);
        });
    }

    public WriteResult write(Collection<String> aggregateKeys, String expectedRevision,
                              Supplier<?> lockedSnapshot, Runnable validateDomain,
                              BooleanSupplier hasActualChanges, Runnable dataAndHistory) {
        if (aggregateKeys.isEmpty()) throw new IllegalArgumentException("잠금 대상이 필요합니다.");
        return transactions.execute(status -> {
            lock(aggregateKeys);
            // Supplier queries must lock and return the COMPLETE aggregate: all codes (including deleted),
            // or all effective-month rows, identifiers, values and both audit timestamps, in key order.
            String before = revisions.revision(lockedSnapshot.get());
            revisions.requireExpected(expectedRevision, before);
            validateDomain.run();
            if (!hasActualChanges.getAsBoolean()) {
                return new WriteResult(false, before, "바뀐 내용이 없습니다.");
            }
            dataAndHistory.run();
            String after = revisions.revision(lockedSnapshot.get());
            if (before.equals(after)) throw new IllegalStateException("실제 변경이 저장되지 않았습니다.");
            aggregateKeys.stream().distinct().sorted().forEach(invalidation::afterCommit);
            return new WriteResult(true, after, null);
        });
    }
    private void lock(Collection<String> keys) {
        if (sourceLocks != null) sourceLocks.lock(keys);
        else keys.stream().distinct().sorted().forEach(locks::lock);
    }
}
