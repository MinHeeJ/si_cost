package com.sicost.meops;

import java.util.List;

public final class ProfileDtos {
    private ProfileDtos() {}
    public record User(String userId, String userName) {}
    public record Role(String roleId, String roleName) {}
    public record Part(String partId, String partName) {}
    public record Profile(String userId, String userName, List<Role> activeRoles, List<Part> parts,
                          String basisMonth, String editGuidance) {}
}
