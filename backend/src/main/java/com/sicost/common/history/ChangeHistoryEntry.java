package com.sicost.common.history;

import java.time.LocalDateTime;

public record ChangeHistoryEntry(String tableName, String targetKey, String type,
                                 String item, String before, String after,
                                 String reason, String userId, LocalDateTime occurredAt) {
}
