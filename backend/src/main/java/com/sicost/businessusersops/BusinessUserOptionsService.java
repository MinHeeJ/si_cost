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
    public BusinessUserOptionsService(BusinessUserOptionsRepository repository, CurrentUserProvider users) {
        this.repository = repository;
        this.users = users;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Options options(String roleId) {
        users.currentUser();
        return new Options(repository.eligible(roleId), repository.activeRoles());
    }
}
