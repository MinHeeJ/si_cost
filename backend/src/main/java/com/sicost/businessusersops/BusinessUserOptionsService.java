package com.sicost.businessusersops;

import com.sicost.common.context.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.businessusersops.BusinessUserOptionsDtos.*;

@Service
public class BusinessUserOptionsService {
    private final BusinessUserOptionsRepository repository;
    private final CurrentUserProvider users;
    private com.sicost.permission.PermissionService permissions;
    @org.springframework.beans.factory.annotation.Autowired
    public void setPermissions(com.sicost.permission.PermissionService permissions) {this.permissions=permissions;}
    public BusinessUserOptionsService(BusinessUserOptionsRepository repository, CurrentUserProvider users) {
        this.repository = repository;
        this.users = users;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Options options(String roleId) {
        users.currentUser();
        return new Options(repository.eligible(roleId), repository.activeRoles());
    }
    @Transactional(readOnly=true)
    public java.util.List<RoleOption> roleOptions() {
        permissions.require("UI-23","INQ");return repository.allActiveRoles();
    }
}
