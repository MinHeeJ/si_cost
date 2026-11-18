package com.sicost.apiops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.context.CurrentUserProvider;
import com.sicost.notificationsops.NotificationDtos.*;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.apiops.ActivityReadDtos.*;

@Service
public class ActivityReadService {
    private final ActivityReadRepository repository;
    private final CurrentUserProvider users;
    private final Clock clock;
    private final boolean manualRunEnabled;
    private final com.sicost.permission.NotificationProjectionService projection;
    public ActivityReadService(ActivityReadRepository repository,CurrentUserProvider users,Clock clock,
        @Value("${AUTOMATION_MANUAL_RUN_ENABLED:false}") boolean manualRunEnabled,com.sicost.permission.NotificationProjectionService projection) {
        this.repository=repository;this.users=users;this.clock=clock;this.manualRunEnabled=manualRunEnabled;this.projection=projection;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Notifications getNotifications(ActivityQueryInput.Query query) {
        String user=users.currentUser().userId();
        var rows=repository.notifications(new RecipientQuery(user,query.size(),query.offset()));
        long total=repository.notificationCount(user);
        var items=rows.stream().map(r->new Item(r.id().toPlainString(),r.typeCode(),r.content(),r.sendMethodCode(),r.read(),
            offset(r.readAt()),offset(r.notifiedAt()),r.screenId()!=null&&(r.linkKey()==null||r.linkKey().matches("[A-Za-z0-9_-]{1,50}"))?new Link(r.screenId(),r.linkKey()):null)).toList();
        return new Notifications(projection.project(items),query.page(),query.size(),total,query.offset()+items.size()<total,now());
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public AutomationRuns getAutomationRuns(ActivityQueryInput.Query query) {
        users.currentUser();
        var rows=repository.automationRuns(query).stream().map(com.sicost.automationrunsops.DailyAutomationDtos.Stored::view).toList();
        long total=repository.automationCount(query);
        return new AutomationRuns(rows,query.page(),query.size(),total,query.offset()+rows.size()<total,manualRunEnabled,now());
    }
    private OffsetDateTime now(){return OffsetDateTime.now(clock.withZone(TimeConfiguration.SEOUL));}
    private static OffsetDateTime offset(LocalDateTime value){return value==null?null:value.atZone(TimeConfiguration.SEOUL).toOffsetDateTime();}
}
