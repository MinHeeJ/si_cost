package com.sicost.common.notifications;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.common.notifications.NotificationPublicationMapper.*;

@Service
public class UploadNotificationService {
    private final NotificationPublicationMapper mapper;
    private final NotificationPublisher publisher;
    public UploadNotificationService(NotificationPublicationMapper mapper,NotificationPublisher publisher) {this.mapper=mapper;this.publisher=publisher;}
    @Transactional(propagation=Propagation.MANDATORY)
    public void applied(long id) {
        var upload=mapper.appliedUpload(id);
        if(upload==null)throw new IllegalStateException("반영 완료 업로드를 확인하지 못했습니다.");
        publisher.publish(new Event(NotificationEventKeys.upload(Long.toString(id)),"UPLD_DONE","업로드 반영 완료",new Link(upload.screenId(),Long.toString(id)),null),
            List.of(new NotificationRecipients.Recipient(upload.userId(),upload.userId(),null,List.of())));
    }
}
