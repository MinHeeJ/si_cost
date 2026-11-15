package com.sicost.apiops;

import com.sicost.automationrunsops.DailyAutomationDtos.Stored;
import com.sicost.notificationsops.NotificationDtos.StoredNotification;
import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.apiops.ActivityReadDtos.*;

@Repository
public class ActivityReadRepository {
    private final ActivityReadMapper mapper;
    public ActivityReadRepository(ActivityReadMapper mapper){this.mapper=mapper;}
    public List<StoredNotification> notifications(RecipientQuery query){return mapper.notifications(query);}
    public long notificationCount(String userId){return mapper.notificationCount(userId);}
    public List<Stored> automationRuns(ActivityQueryInput.Query query){return mapper.automationRuns(query);}
    public long automationCount(ActivityQueryInput.Query query){return mapper.automationCount(query);}
}
