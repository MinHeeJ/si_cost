package com.sicost.common.persistence;

import java.time.Clock;
import java.time.LocalDateTime;

public record AuditFields(String userId, LocalDateTime occurredAt) {
    public static AuditFields now(String serverUserId, Clock clock) {
        return new AuditFields(serverUserId, LocalDateTime.now(clock));
    }
}
