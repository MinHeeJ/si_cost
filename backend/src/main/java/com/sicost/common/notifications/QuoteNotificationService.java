package com.sicost.common.notifications;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.common.notifications.NotificationPublicationMapper.*;

@Service
public class QuoteNotificationService {
    private final NotificationPublicationMapper mapper;
    private final NotificationPublisher publisher;
    public QuoteNotificationService(NotificationPublicationMapper mapper,NotificationPublisher publisher) {this.mapper=mapper;this.publisher=publisher;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void rejected(String id) {
        var rejection=mapper.quoteRejection(id);
        if(rejection==null)throw new IllegalStateException("실제 견적 반려 이력을 확인하지 못했습니다.");
        publisher.publish(new Event(NotificationEventKeys.quoteRejected(id,rejection.historySn()),"QUOTE_REJ","기회 "+id+" · 견적 반려 · 상세 확인",new Link("UI-29",id),null),
            List.of(new NotificationRecipients.Recipient(rejection.userId(),rejection.userId(),null,List.of())));
    }
}
