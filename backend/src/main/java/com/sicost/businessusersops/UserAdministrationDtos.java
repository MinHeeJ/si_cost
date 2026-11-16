package com.sicost.businessusersops;

import com.sicost.businessusers.BusinessUserDtos.AssignedPart;
import java.time.LocalDateTime;
import java.util.List;

public final class UserAdministrationDtos {
    private UserAdministrationDtos() {}
    public record AssignedProject(String projectId, int scopeSn) {}
    public record Scopes(String userId, List<AssignedPart> assignedParts, List<AssignedProject> assignedProjects, String revision) {}
    public record Activity(long logId, String userId, String actedAt, String actionType, String screenId, String targetDescription) {}
    public record Activities(List<Activity> items, long total, int page, int size) {}
    public record ActivityQuery(String userId, int page, int size, long offset) {}
    public record StatusItem(String userId, String expectedRevision) {}
    public record StatusRequest(String action, List<StatusItem> items) {}
    public record StatusResult(String userId, String outcome, String code, String reason, boolean changed, String revision) {}
    public record StatusResults(int applied, int unchanged, int rejected, List<StatusResult> items) {}
    public record StateWrite(String userId, String status, boolean unlock, String actor, LocalDateTime now) {}
}
