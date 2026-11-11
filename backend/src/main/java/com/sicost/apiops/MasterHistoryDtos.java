package com.sicost.apiops;

import java.util.List;

public final class MasterHistoryDtos {
    private MasterHistoryDtos() {}
    public record Query(String targetType, String targetId, int page, int size, long offset) {}
    public record Entry(long historyId, String targetTable, String targetKey, String changeType,
                        String itemName, String beforeValue, String afterValue, String reason,
                        String actorId, String actorName, String createdAt) {}
    public record Page(List<Entry> items, long total, int page, int size) {}
}
