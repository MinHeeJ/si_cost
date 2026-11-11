package com.sicost.businessusersops;

import java.util.List;

public final class BusinessUserOptionsDtos {
    private BusinessUserOptionsDtos() {}
    public record UserOption(String userId, String name, String label) {}
    public record RoleOption(String roleId, String name) {}
    public record Options(List<UserOption> items, List<RoleOption> roleOptions) {}
}
