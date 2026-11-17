package com.sicost.common.concurrent;

import com.sicost.common.api.ApiException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Physical part/header/standard locks precede the existing source advisory namespaces. */
@Service
public class SettlementSourceLocks {
    public static final String ROLE_RESOURCE = "TB_SY_ROLE";
    public static final String MENU_RESOURCE = "TB_SY_MENU";
    public record Sources(List<String> partIds, List<String> developerIds, List<String> assignmentIds,
                          List<String> projectIds, List<String> payrollMonths, List<String> userIds) {
        public Sources(List<String> partIds, List<String> developerIds, List<String> assignmentIds,
                       List<String> projectIds, List<String> payrollMonths) {
            this(partIds, developerIds, assignmentIds, projectIds, payrollMonths, List.of());
        }
        public Sources {
            partIds = ordered(partIds); developerIds = ordered(developerIds); assignmentIds = ordered(assignmentIds);
            projectIds = ordered(projectIds); payrollMonths = ordered(payrollMonths); userIds = ordered(userIds);
        }
        public boolean empty() {
            return partIds.isEmpty() && developerIds.isEmpty() && assignmentIds.isEmpty() && projectIds.isEmpty() && payrollMonths.isEmpty() && userIds.isEmpty();
        }
    }
    private final SettlementSourceLockMapper mapper;
    private final AggregateLockMapper advisory;
    public SettlementSourceLocks(SettlementSourceLockMapper mapper, AggregateLockMapper advisory) {
        this.mapper = mapper; this.advisory = advisory;
    }
    /** Call once with the complete old/new source population before taking any other source locks. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(Collection<String> aggregateKeys) {
        List<String> keys = protectionKeys(aggregateKeys);
        if (keys.isEmpty()) throw new IllegalArgumentException("잠금 대상이 필요합니다.");
        var held = currentLocks();
        // 사용자·역할·메뉴 명령은 잠금 순서(역할 → 메뉴 → 사용자)가 정해져 있어 뒤늦게 대상을 더하면 교착 위험이 있다: 처음부터 전체 대상을 요구한다.
        // 그 밖의 흐름(예: 배정 후보 확정이 새 배정 ID 를 두 번째 잠금에서 더하는 것)은 요구사항 17 이전과 같게 대상을 더 잠글 수 있다.
        if (held != null && isOrderedCommand(aggregateKeys) && !held.keys().containsAll(keys))
            throw new ApiException(409,"STALE_WRITE","잠금 대상이 추가되었습니다. 전체 대상을 다시 조회해 새 transaction으로 요청해 주세요.");
        Sources sources = new Sources(values(keys, "PART:"), values(keys, "DEV:"), values(keys, "ASGN:"),
            values(keys, "PROJECT:"), values(keys, "PAY:"), values(keys, "USER:"));
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
        if (held == null && org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new HeldLocks(keys));
    }
    private record HeldLocks(List<String> keys) implements org.springframework.transaction.support.TransactionSynchronization {}
    private HeldLocks currentLocks() {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) return null;
        return org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().stream()
            .filter(HeldLocks.class::isInstance).map(HeldLocks.class::cast).findFirst().orElse(null);
    }
    private static boolean isOrderedCommand(Collection<String> aggregateKeys) {
        return aggregateKeys.stream().anyMatch(k -> k.startsWith("USER:") || k.equals(ROLE_RESOURCE) || k.equals(MENU_RESOURCE));
    }
    public static List<String> protectionKeys(Collection<String> aggregateKeys) {
        var keys = new TreeSet<>(ordered(aggregateKeys));
        if (keys.stream().anyMatch(k -> k.startsWith("USER:") || k.startsWith("PROJECT:") || k.startsWith("PART:")))
            keys.add(ROLE_RESOURCE);
        if (keys.contains(ROLE_RESOURCE) || keys.contains(MENU_RESOURCE)) {
            keys.add(ROLE_RESOURCE);
            keys.add(MENU_RESOURCE);
        }
        return List.copyOf(keys);
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
