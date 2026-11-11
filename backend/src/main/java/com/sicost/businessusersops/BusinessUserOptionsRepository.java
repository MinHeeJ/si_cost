package com.sicost.businessusersops;

import java.util.List;
import org.springframework.stereotype.Repository;
import static com.sicost.businessusersops.BusinessUserOptionsDtos.*;

@Repository
public class BusinessUserOptionsRepository {
    private final BusinessUserOptionsMapper mapper;
    public BusinessUserOptionsRepository(BusinessUserOptionsMapper mapper) { this.mapper = mapper; }
    public List<UserOption> eligible(String roleId) { return mapper.eligible(roleId); }
    public List<RoleOption> activeRoles() { return mapper.activeRoles(); }
}
