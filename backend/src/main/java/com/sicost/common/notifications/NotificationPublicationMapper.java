package com.sicost.common.notifications;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationPublicationMapper {
    record QuoteRejection(String userId,int historySn,String opportunityName,String reason) {}
    QuoteRejection quoteRejection(String opportunityId);
    record AppliedUpload(String userId,String screenId) {}
    AppliedUpload appliedUpload(long uploadId);
    record Link(String screenId, String key) {}
    record Dunning(String billId, int step, int serial, boolean resend) {}
    record Event(String key, String type, String content, Link link, Dunning dunning) {}
    record Publication(UUID taskId, Event event, NotificationRecipients.Recipient recipient, LocalDateTime at) {}
    record NotificationConnection(UUID taskId, BigDecimal notificationId) {}
    List<NotificationRecipients.Role> eligibleRoles();
    List<NotificationRecipients.Role> activeUsers();
    List<NotificationRecipients.Scope> recipientScopes();
    boolean registeredScreen(String screenId);
    int reserveSystem(Publication publication);
    int reserveSkipped(Publication publication);
    BigDecimal insertNotification(Publication publication);
    int connectNotification(NotificationConnection connection);
}
