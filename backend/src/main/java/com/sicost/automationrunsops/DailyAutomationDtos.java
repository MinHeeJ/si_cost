package com.sicost.automationrunsops;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.*;

public final class DailyAutomationDtos {
    private DailyAutomationDtos() {}
    public record Key(String jobCode, LocalDate runDate) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Run(String jobCode, LocalDate runDate, String state, int lastStep,
                      OffsetDateTime startedAt, OffsetDateTime endedAt, String errorMessage) {}
    public record CommandResult(Run run, boolean accepted, boolean completed) {}
    public record Stored(String jobCode, LocalDate runDate, String state, int lastStep,
                         LocalDateTime startedAt, LocalDateTime endedAt, String errorMessage) {
        public Key key() { return new Key(jobCode, runDate); }
        public Run view() { return new Run(jobCode,runDate,state,lastStep,
            startedAt.atOffset(ZoneOffset.ofHours(9)),endedAt==null?null:endedAt.atOffset(ZoneOffset.ofHours(9)),errorMessage); }
    }
    public record RunWrite(Key key, String state, int lastStep, LocalDateTime at, LocalDateTime endedAt, String errorMessage) {}
    public record Item(String id, String projectId, String developerId) {}
    public record Project(String id, String state, LocalDate endDate) {}
    public record Assignment(String id,String projectId,String developerId,LocalDate startDate,LocalDate endDate,
                             String state,String projectState,BigDecimal mm,BigDecimal price) {}
    public record Pointer(String id,BigDecimal mm,BigDecimal price,LocalDateTime at) {}
    public record ProjectEnd(String id,int serial,String before,LocalDate date,LocalDateTime at) {}
    public record Notification(String id,String userId,String type,String content,String screenId,String linkKey,
                               String method,String read,LocalDateTime readAt,LocalDateTime notifiedAt,
                               String regUserId,LocalDateTime registeredAt,String modUserId,LocalDateTime modifiedAt) {}
}
