package com.sicost.menumanagementops;

import java.util.List;
import com.sicost.menumanagement.MenuManagementRepository;
import com.sicost.menumanagement.MenuManagementDtos.Stored;
import com.sicost.common.concurrent.RevisionService;
import com.sicost.shared.MenuService;
import org.springframework.stereotype.Repository;
import static com.sicost.menumanagementops.MenuRegistrationDtos.*;

@Repository
public class MenuRegistrationRepository {
    private final MenuRegistrationMapper mapper;
    private final MenuManagementRepository menus;
    private final RevisionService revisions;
    private final MenuService screens;
    public MenuRegistrationRepository(MenuRegistrationMapper mapper, MenuManagementRepository menus,
                                      RevisionService revisions, MenuService screens) {
        this.mapper = mapper; this.menus = menus; this.revisions = revisions; this.screens = screens;
    }
    public List<Stored> tree() { return menus.tree(); }
    public String revision(List<Stored> tree) { return revisions.revision(tree); }
    public List<MenuService.ScreenRegistration> screens() { return screens.screenRegistrations(); }
    public List<String> roleIds() { return mapper.roleIds(); }
    public void insertMenu(NewMenu menu) {
        if (mapper.insertMenu(menu) != 1) throw new IllegalStateException("메뉴를 등록하지 못했습니다.");
    }
    public void insertPermission(NewPermission permission) {
        if (mapper.insertPermission(permission) != 1) throw new IllegalStateException("메뉴 권한을 등록하지 못했습니다.");
    }
}
