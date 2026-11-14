package com.sicost.tentativeassignmentsops;

import java.time.LocalDateTime;
import java.util.List;

public final class TentativeAssignmentDtos {
    private TentativeAssignmentDtos() {}
    public record Release(String expectedRevision, String reason) {}
    public record Result(boolean changed, String revision, List<String> warnings, String opportunityId, String tentativeId) {}
    public record State(String id, String opportunityId, String developerId, String status,
                        String opportunityStatus, LocalDateTime processedAt) {}
    public record Mutation(String id, String opportunityId, String actor, LocalDateTime now) {}
}
