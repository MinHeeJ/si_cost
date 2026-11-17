package com.sicost.menumanagement;

import java.time.LocalDateTime;
import java.util.List;

public final class MenuManagementDtos {
    private MenuManagementDtos() {}
    public record Stored(String menuId, String parentId, String name, int level, String screenId,
                         int sortOrder, boolean use, boolean pcOnly, boolean locked, String icon,
                         String regUserId, LocalDateTime regDttm, String modUserId, LocalDateTime modDttm) {}
    public record ManagedMenu(String menuId, String parentId, String name, int level, String screenId,
                              int sortOrder, boolean use, boolean pcOnly, boolean locked, String icon) {}
    public record ManagedMenuTree(List<ManagedMenu> items, String revision, boolean developmentMode, boolean accessEnforcement) {}
    public record MenuInput(String menuId, String name, int level, String parentId, String screenId,
                            int sortOrder, Boolean use, Boolean pcOnly) {}
    public record PutTree(List<MenuInput> items, String expectedRevision) {}
    public record SaveResult(ManagedMenuTree value, String revision, boolean changed) {}
    public record Mutation(MenuInput input, String useYn, String pcOnlyYn, String actor, LocalDateTime now) {}
}
