package com.sicost.apiops;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public final class PeopleHistoryDtos {
    private PeopleHistoryDtos() {}
    public record Query(String targetType, String targetId, int page, int size, long offset) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Entry(long historyId, String targetTable, String targetKey, String changeType,
                        String itemName, String beforeValue, String afterValue, String reason,
                        String actorId, String actorName, String createdAt) {}
    public record Page(List<Entry> items, long total, int page, int size) {}
}
