package com.sicost.notificationsops;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public final class NotificationDtos {
    private NotificationDtos() {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UnreadCount(long unreadCount, Long receivableCount, Boolean currentMonthUnclosed, OffsetDateTime observedAt) {
        public UnreadCount(long unreadCount,long receivableCount,boolean currentMonthUnclosed,OffsetDateTime observedAt) {this(unreadCount,Long.valueOf(receivableCount),Boolean.valueOf(currentMonthUnclosed),observedAt);}
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Link(String screenId, String linkKey) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Item(String id, String typeCode, String content, String sendMethodCode, boolean read,
                       OffsetDateTime readAt, OffsetDateTime notifiedAt, Link link) {}
    public record ReadResult(Item notification, boolean changed) {}
    public record ReadAllResult(int changedCount, OffsetDateTime cutoffAt) {}
    public record StoredNotification(BigDecimal id, String typeCode, String content, String sendMethodCode,
                                     boolean read, LocalDateTime readAt, LocalDateTime notifiedAt,
                                     String screenId, String linkKey) {}
    public record ReadWrite(String userId, BigDecimal id, LocalDateTime readAt, LocalDateTime modifiedAt) {}
    public record ReadAllWrite(String userId, LocalDateTime cutoffAt) {}
}
