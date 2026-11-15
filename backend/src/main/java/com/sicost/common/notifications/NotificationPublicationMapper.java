package com.sicost.common.notifications;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationPublicationMapper {
    record Link(String screenId, String key) {}
    record Dunning(String billId, int step, int serial, boolean resend) {}
    record Event(String key, String type, String content, Link link, Dunning dunning) {}
    record Publication(UUID taskId, Event event, NotificationRecipients.Recipient recipient, LocalDateTime at) {}
    record NotificationConnection(UUID taskId, BigDecimal notificationId) {}
    List<NotificationRecipients.Role> eligibleRoles();
    List<NotificationRecipients.Scope> recipientScopes();
    boolean registeredScreen(String screenId);
    int reserveSystem(Publication publication);
    BigDecimal insertNotification(Publication publication);
    int connectNotification(NotificationConnection connection);
}
