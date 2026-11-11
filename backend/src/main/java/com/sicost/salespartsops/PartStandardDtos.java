package com.sicost.salespartsops;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class PartStandardDtos {
    private PartStandardDtos() {}
    public record Header(String id, String name, String hqYn, String useYn, String regUserId,
                         LocalDateTime regDttm, String modUserId, LocalDateTime modDttm) {}
    public record Stored(String partId, String month, String endMonth, String leaderUserId,
                         BigDecimal profitRate, BigDecimal salesExpenseAmount, String regUserId,
                         LocalDateTime regDttm, String modUserId, LocalDateTime modDttm) {}
    public record Snapshot(Header part, List<Stored> standards) {}
    public record Standard(String effectiveMonth, String endMonth, String leaderUserId, String leaderName,
                           String profitRate, String salesExpenseAmount, String status) {}
    public record Part(String id, String name, String useYn, String currentMonth, Standard currentStandard,
                       Standard scheduledChange, long personCount, long projectCount, String revision) {}
    public record Detail(Part part, List<Standard> standards, String minMonth, String revision) {}
    public record Result(boolean changed, String message, Detail part) {}
    public record DefaultError(String source, String field, String message) {}
    public record Defaults(String effectiveMonth, String minMonth, String profitRate, String salesExpenseAmount,
                           List<DefaultError> defaultErrors) {}
    public record Option(String id, String name, String hqYn, String firstEffectiveMonth) {}
    public record Options(List<Option> items) {}
    public record Save(String effectiveMonth, String leaderUserId, BigDecimal profitRate,
                       BigDecimal salesExpenseAmount, String expectedRevision) {}
    public record Mutation(String partId, String month, String endMonth, Save input, String actor, LocalDateTime now) {}
}
