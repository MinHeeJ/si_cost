package com.sicost.common.concurrent;

import com.sicost.common.api.ApiException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Physical part/header/standard locks precede the existing source advisory namespaces. */
@Service
public class SettlementSourceLocks {
    public record Sources(List<String> partIds, List<String> developerIds, List<String> assignmentIds,
                          List<String> projectIds, List<String> payrollMonths) {
        public Sources {
            partIds = ordered(partIds); developerIds = ordered(developerIds); assignmentIds = ordered(assignmentIds);
            projectIds = ordered(projectIds); payrollMonths = ordered(payrollMonths);
        }
        public boolean empty() {
            return partIds.isEmpty() && developerIds.isEmpty() && assignmentIds.isEmpty() && projectIds.isEmpty() && payrollMonths.isEmpty();
        }
    }
    private final SettlementSourceLockMapper mapper;
    private final AggregateLockMapper advisory;
    private final Object transactionKeys = new Object();
    public SettlementSourceLocks(SettlementSourceLockMapper mapper, AggregateLockMapper advisory) {
        this.mapper = mapper; this.advisory = advisory;
    }
    /** Call once with the complete old/new source population before taking any other source locks. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(Collection<String> aggregateKeys) {
        List<String> keys = ordered(aggregateKeys);
        if (keys.isEmpty()) throw new IllegalArgumentException("잠금 대상이 필요합니다.");
        var transaction = org.springframework.transaction.support.TransactionSynchronizationManager.getResource(transactionKeys);
        if (transaction instanceof Set<?> held && held.containsAll(keys)) return;
        Sources sources = new Sources(values(keys, "PART:"), values(keys, "DEV:"), values(keys, "ASGN:"),
            values(keys, "PROJECT:"), values(keys, "PAY:"));
        List<String> parts = sources.empty() ? List.of() : ordered(mapper.partIds(sources));
        if (!parts.isEmpty()) {
            mapper.lockParts(parts);
            mapper.lockHeaders(parts);
            mapper.lockStandards(parts);
        }
        keys.forEach(advisory::lock);
        if (!sources.empty() && !parts.equals(ordered(mapper.partIds(sources)))) {
            throw new ApiException(409, "STATE_CONFLICT", "잠금 중 인력 소속이 변경되었습니다. 다시 조회해 주세요.", "partId", null);
        }
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            @SuppressWarnings("unchecked")
            Set<String> held = transaction == null ? new TreeSet<>() : (Set<String>) transaction;
            held.addAll(keys);
            if (transaction == null) org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(transactionKeys, held);
            if (transaction == null) org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                public void suspend() { org.springframework.transaction.support.TransactionSynchronizationManager.unbindResourceIfPossible(transactionKeys); }
                public void resume() { org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(transactionKeys, held); }
                public void afterCompletion(int status) { org.springframework.transaction.support.TransactionSynchronizationManager.unbindResourceIfPossible(transactionKeys); }
            });
        }
    }
    private static List<String> values(List<String> keys, String prefix) {
        return keys.stream().filter(k -> k.startsWith(prefix)).map(k -> k.substring(prefix.length())).toList();
    }
    private static List<String> ordered(Collection<String> values) {
        Objects.requireNonNull(values, "잠금 대상이 필요합니다.");
        if (values.stream().anyMatch(v -> v == null || v.isBlank())) throw new IllegalArgumentException("빈 잠금 대상은 허용하지 않습니다.");
        return values.stream().distinct().sorted().toList();
    }
}
