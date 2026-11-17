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
    private MonthCloseBoundary boundary;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setMonthCloseBoundary(MonthCloseBoundary boundary) { this.boundary = boundary; }
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
    public record GuardedWrite(Collection<String> aggregateKeys, String expectedRevision,
        Supplier<?> lockedSnapshot, Runnable beforeRevision, Runnable validateDomain,
        BooleanSupplier hasActualChanges, Runnable dataAndHistory) {}

    public WriteResult writeGuarded(GuardedWrite command) {
        if (command.aggregateKeys().isEmpty()) throw new IllegalArgumentException("잠금 대상이 필요합니다.");
        return transactions.execute(status -> {
            lock(command.aggregateKeys());
            command.beforeRevision().run();
            String before = revisions.revision(command.lockedSnapshot().get());
            revisions.requireExpected(command.expectedRevision(), before);
            command.validateDomain().run();
            if (!command.hasActualChanges().getAsBoolean()) return new WriteResult(false, before, "바뀐 내용이 없습니다.");
            command.dataAndHistory().run();
            String after = revisions.revision(command.lockedSnapshot().get());
            if (before.equals(after)) throw new IllegalStateException("실제 변경이 저장되지 않았습니다.");
            command.aggregateKeys().stream().distinct().sorted().forEach(invalidation::afterCommit);
            return new WriteResult(true, after, null);
        });
    }

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
    /** One caller-owned transaction for checks, close or reopen, with no SHARE promotion. */
    public <T> T exclusive(Collection<String> aggregateKeys, Supplier<T> command) {
        var keys = aggregateKeys.stream().distinct().sorted().toList();
        if (keys.isEmpty()) throw new IllegalArgumentException("잠금 대상이 필요합니다.");
        return transactions.execute(status -> {
            if (boundary == null) throw new IllegalStateException("마감 경계가 구성되지 않았습니다.");
            boundary.exclusive();
            lock(keys);
            return command.get();
        });
    }
    private void lock(Collection<String> keys) {
        if (boundary != null) boundary.shared();
        if (sourceLocks != null) sourceLocks.lock(keys);
        else SettlementSourceLocks.protectionKeys(keys).forEach(locks::lock);
    }
}
