package com.sicost.notificationsops;

import java.math.BigDecimal;
import org.springframework.stereotype.Repository;
import static com.sicost.notificationsops.NotificationDtos.*;

@Repository
public class NotificationRepository {
    private final NotificationMapper mapper;
    public NotificationRepository(NotificationMapper mapper) { this.mapper=mapper; }
    public long unreadCount(String userId) { return mapper.unreadCount(userId); }
    public long receivableCount() { return mapper.receivableCount(); }
    public boolean currentMonthUnclosed(String month) { return mapper.currentMonthUnclosed(month); }
    public StoredNotification lock(String userId, BigDecimal id) { return mapper.lock(userId,id); }
    public int markRead(ReadWrite write) { return mapper.markRead(write); }
    public int markAllRead(ReadAllWrite write) { return mapper.markAllRead(write); }
    public boolean registeredScreen(String screenId) { return mapper.registeredScreen(screenId); }
}
