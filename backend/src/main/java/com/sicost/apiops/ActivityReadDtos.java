package com.sicost.apiops;

import com.sicost.automationrunsops.DailyAutomationDtos.Run;
import com.sicost.notificationsops.NotificationDtos.Item;
import java.time.OffsetDateTime;
import java.util.List;

public final class ActivityReadDtos {
    private ActivityReadDtos() {}
    public record Notifications(List<Item> items,int page,int size,long total,boolean hasMore,OffsetDateTime observedAt) {}
    public record AutomationRuns(List<Run> items,int page,int size,long total,boolean hasMore,boolean manualRunEnabled,OffsetDateTime observedAt) {}
    public record RecipientQuery(String userId,int size,long offset) {}
}
