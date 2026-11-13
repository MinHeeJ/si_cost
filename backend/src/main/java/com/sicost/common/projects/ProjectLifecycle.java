package com.sicost.common.projects;

import java.time.LocalDate;

/** Synchronous notifications inside the caller's project transaction. */
public final class ProjectLifecycle {
    private ProjectLifecycle() {}
    public record Registered(String projectId) {}
    public record PeriodChanged(String projectId, LocalDate startDate, LocalDate endDate) {}
}
