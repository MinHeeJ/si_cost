package com.sicost.notificationsops;

import com.sicost.common.TimeConfiguration;
import com.sicost.common.api.ApiException;
import com.sicost.common.concurrent.CommitInvalidation;
import com.sicost.common.context.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.sicost.notificationsops.NotificationDtos.*;

@Service
public class NotificationService {
    private final NotificationRepository repository;
    private final CurrentUserProvider users;
    private final Clock clock;
    private final CommitInvalidation invalidation;
    private final com.sicost.permission.PermissionService permissions;
    private final com.sicost.permission.NotificationProjectionService projection;
    public NotificationService(NotificationRepository repository, CurrentUserProvider users, Clock clock, CommitInvalidation invalidation,
            com.sicost.permission.PermissionService permissions,com.sicost.permission.NotificationProjectionService projection) {
        this.repository=repository;this.users=users;this.clock=clock;this.invalidation=invalidation;this.permissions=permissions;this.projection=projection;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public UnreadCount getNotificationUnreadCount() {
        String userId=users.currentUser().userId();
        var now=now();
        String month=YearMonth.from(now).format(DateTimeFormatter.ofPattern("yyyyMM"));

        var granted=permissions.capabilities();
        Long receivables=granted.screens().getOrDefault("UI-13",java.util.Set.of()).contains("INQ")?repository.receivableCount():null;
        Boolean unclosed=granted.scope().all() && granted.screens().getOrDefault("UI-14",java.util.Set.of()).contains("INQ")?repository.currentMonthUnclosed(month):null;
        return new UnreadCount(repository.unreadCount(userId),receivables,unclosed,now);
    }
    @Transactional
    public ReadResult saveNotificationRead(String id) {
        if(id==null||!id.matches("[0-9]{1,12}"))throw new ApiException(400,"VALIDATION_ERROR","알림 번호를 확인해 주세요.","id",null);
        String userId=users.currentUser().userId();
        BigDecimal key=new BigDecimal(id);
        StoredNotification row=repository.lock(userId,key);
        if(row==null)throw new ApiException(404,"NOT_FOUND","알림을 찾을 수 없습니다.");
        var readAt=row.readAt();
        boolean changed=!row.read();
        if(changed) {
            var time=now().toLocalDateTime();
            if(readAt==null)readAt=time;
            if(repository.markRead(new ReadWrite(userId,key,readAt,time))!=1)throw new IllegalStateException("알림 읽음 결과를 확인할 수 없습니다.");
            invalidation.afterCommit("notifications:"+userId);
        }
        return new ReadResult(projection.project(new Item(row.id().toPlainString(),row.typeCode(),row.content(),row.sendMethodCode(),true,
            offset(readAt),offset(row.notifiedAt()),link(row))),changed);
    }
    @Transactional
    public ReadAllResult saveNotificationsReadAll() {
        var cutoff=now();
        String userId=users.currentUser().userId();
        int changed=repository.markAllRead(new ReadAllWrite(userId,cutoff.toLocalDateTime()));
        if(changed>0)invalidation.afterCommit("notifications:"+userId);
        return new ReadAllResult(changed,cutoff);
    }
    private Link link(StoredNotification row) {
        String screen=row.screenId(),key=row.linkKey();
        if(screen==null||!screen.matches("UI-[0-9]{2}")||key!=null&&(key.length()>50||!key.matches("[A-Za-z0-9_-]+")))return null;
        return repository.registeredScreen(screen) && permissions.allows(screen,"INQ")?new Link(screen,key):null;
    }
    private OffsetDateTime now() { return OffsetDateTime.now(clock.withZone(TimeConfiguration.SEOUL)); }
    private OffsetDateTime offset(LocalDateTime value) { return value==null?null:value.atZone(TimeConfiguration.SEOUL).toOffsetDateTime(); }
}
