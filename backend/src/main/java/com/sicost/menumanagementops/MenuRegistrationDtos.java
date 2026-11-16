package com.sicost.menumanagementops;

import java.time.LocalDateTime;
import java.util.List;

public final class MenuRegistrationDtos {
    private MenuRegistrationDtos() {}
    public record Create(int level, String name, String parentId, String screenId, boolean pcOnly, String expectedRevision) {}
    public record AvailableScreen(String screenId, String name) {}
    public record AvailableScreens(List<AvailableScreen> items, String revision) {}
    public record NewMenu(String menuId, Create input, int sortOrder, String actor, LocalDateTime now) {}
    public record NewPermission(String roleId, String menuId, String actor, LocalDateTime now) {}
}
