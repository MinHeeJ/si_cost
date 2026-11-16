package com.sicost.notificationsops;

import java.math.BigDecimal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import static com.sicost.notificationsops.NotificationDtos.*;

@Mapper
public interface NotificationMapper {
    long unreadCount(String userId);
    long receivableCount();
    boolean currentMonthUnclosed(String month);
    StoredNotification lock(@Param("userId") String userId, @Param("id") BigDecimal id);
    int markRead(ReadWrite write);
    int markAllRead(ReadAllWrite write);
    boolean registeredScreen(String screenId);
}
