package com.sicost.businessusers;

import java.time.LocalDateTime;
import java.util.List;

public final class BusinessUserDtos {
    private BusinessUserDtos() {}
    public record Stored(String userId, String name, String email, String status, LocalDateTime lastLoginAt,
                         String regUserId, LocalDateTime regDttm, String modUserId, LocalDateTime modDttm) {}
    public record Role(String roleId, String name, String useYn) {}
    public record Snapshot(String userId, String name, String email, LocalDateTime regDttm,
                           LocalDateTime modDttm, List<String> roleIds) {}
    public record User(String userId, String name, String email, List<String> roles, List<String> businessRoleIds,
                       String status, String lastLoginAt, String revision) {}
    public record Page(List<User> items, long total, int page, int size) {}
    public record Result(boolean changed, String message, User user) {}
    public record Filter(String q, String businessRole, String status, int page, int size,
                         String sort, String direction, long offset) {}
    public record Create(String userId, String name, String email, List<String> businessRoleIds) {}
    public record Edit(String name, String email, List<String> businessRoleIds, String expectedRevision) {}
    public record NewUser(Create input, String passwordHash, String actor, LocalDateTime now) {}
    public record Mutation(String userId, Edit input, String actor, LocalDateTime now) {}
    public record RoleLink(String userId, String roleId, String actor, LocalDateTime now) {}
}
