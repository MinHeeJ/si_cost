package com.sicost.common.notifications;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.common.notifications.NotificationPublicationMapper.*;

/** Requirement 16 calls this only in the successful prediction completion transaction. */
@Service
public class PredictionNotificationAdapter {
    private final NotificationPublisher publisher;
    public PredictionNotificationAdapter(NotificationPublisher publisher) {this.publisher=publisher;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void predictionCompleted(long questionId,String questionUserId) {
        publisher.publish(new Event(NotificationEventKeys.prediction(Long.toString(questionId)),"AI_DONE","AI 예측 완료",new Link("UI-26",Long.toString(questionId)),null),
            List.of(new NotificationRecipients.Recipient(questionUserId,questionUserId,null,List.of())));
    }
}
