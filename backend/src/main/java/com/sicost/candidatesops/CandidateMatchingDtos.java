package com.sicost.candidatesops;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class CandidateMatchingDtos {
    private CandidateMatchingDtos() {}
    public record Shortage(String jobCode, String firstShortageMonth, String maximumShortage,
        LocalDate availableUntil, int availableCandidateCount, int activeCandidateCount) {}
    public record ShortagePage(List<Shortage> items, int total, int page, int size,
        boolean includeUnfilled, String from, int months) {}
    public record Position(String opportunityId, String name, int needSn, String probability, String jobCode,
        String gradeCode, String startMonth, String endMonth, LocalDate availableDate, String effectiveRate,
        String candidateCost, String profitRate, boolean belowApprovalThreshold, Integer gradeDistance) {}
    public record PositionsResult(String candidateId, List<Position> items, int total, int limit) {}
    public record Candidate(String id, String jobCode, String gradeCode, String employeeType,
        BigDecimal desiredRate, LocalDate availableDate, String contactStatus, Integer gradeOrder) {}
    public record Need(String opportunityId, String name, int needSn, BigDecimal probability, String jobCode,
        String gradeCode, String startMonth, int months, BigDecimal expectedRate, String defaultRate, Integer gradeOrder) {}
    public record CodeValue(String code, BigDecimal value) {}
    public record Counts(int available, int active) {}
    public record CountQuery(String jobCode, LocalDate until) {}
    public record StandardQuery(String code, String month) {}
    public record JobOrder(String jobCode, Integer sortOrder) {}
}
