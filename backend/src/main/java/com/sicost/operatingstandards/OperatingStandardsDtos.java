package com.sicost.operatingstandards;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class OperatingStandardsDtos {
    private OperatingStandardsDtos() {}
    public record Standard(String key, String name, String value, String unitNm, String effectiveMonth,
                           String description, String keyRevision) {}
    public record Items(List<Standard> items) {}
    public record Version(String key, String value, String unitNm, String effectiveMonth, String status, String revision) {}
    public record History(List<Version> items, String keyRevision) {}
    public record Change(String key, BigDecimal value, String expectedRevision, String field) {}
    public record Save(String effectiveMonth, List<Change> changes) {}
    public record Result(boolean changed, String message, List<Standard> items) {}
    public record Stored(String key, String month, String name, BigDecimal value, String unitNm, String description,
                         String regUserId, LocalDateTime regDttm, String modUserId, LocalDateTime modDttm) {}
    public record Mutation(Stored original, String month, BigDecimal value, String actor, LocalDateTime now) {}
}
