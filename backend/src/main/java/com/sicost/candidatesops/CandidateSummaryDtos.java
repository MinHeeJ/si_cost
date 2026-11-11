package com.sicost.candidatesops;

import java.time.LocalDate;

public final class CandidateSummaryDtos {
    private CandidateSummaryDtos() {}
    public record Summary(long total, long inProgress, long interviewScheduled,
                          long availableByNextMonthStart, long overdueContact) {}
    public record Basis(LocalDate nextMonthStart, LocalDate overdueBefore) {}
}
