package com.sicost.apiops;

import com.sicost.automationrunsops.DailyAutomationDtos.Stored;
import com.sicost.notificationsops.NotificationDtos.StoredNotification;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import static com.sicost.apiops.ActivityReadDtos.*;

@Mapper
public interface ActivityReadMapper {
    List<StoredNotification> notifications(RecipientQuery query);
    long notificationCount(String userId);
    List<Stored> automationRuns(ActivityQueryInput.Query query);
    long automationCount(ActivityQueryInput.Query query);
}
