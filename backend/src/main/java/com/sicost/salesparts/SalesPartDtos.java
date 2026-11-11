package com.sicost.salesparts;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class SalesPartDtos {
    private SalesPartDtos() {}
    public record Stored(String id, String name, String hqYn, String useYn, String regUserId,
                         LocalDateTime regDttm, String modUserId, LocalDateTime modDttm) {}
    public record StoredStandard(String partId, String month, String endMonth, String leaderUserId,
                                 BigDecimal profitRate, BigDecimal salesExpenseAmount, String regUserId,
                                 LocalDateTime regDttm, String modUserId, LocalDateTime modDttm) {}
    public record Standard(String effectiveMonth, String endMonth, String leaderUserId, String leaderName,
                           String profitRate, String salesExpenseAmount, String status) {}
    public record Part(String id, String name, String useYn, String currentMonth, Standard currentStandard,
                       Standard scheduledChange, long personCount, long projectCount, String revision) {}
    public record Detail(Part part, List<Standard> standards, String minMonth, String revision) {}
    public record Page(List<Part> items, long total, int page, int size) {}
    public record Result(boolean changed, String message, Detail part) {}
    public record Query(String q, String useYn, int page, int size, String sort, String direction) {}
    public record Create(String name, String effectiveMonth, String leaderUserId,
                         BigDecimal profitRate, BigDecimal salesExpenseAmount) {}
    public record Patch(String name, String useYn, String expectedRevision) {}
    public record Counts(long personCount, long projectCount) {}
    public record Mutation(String id, String name, String useYn, String actor, LocalDateTime now) {}
    public record Registration(String id, Create input, String month, String actor, LocalDateTime now) {}
    public record Snapshot(Stored part, List<StoredStandard> standards) {}
}
