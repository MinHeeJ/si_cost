package com.sicost.common.context;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {
    public record CurrentUser(String userId, String name, List<String> roleIds,
                              boolean developmentMode) {
        public String dataScope() { return "ALL"; }
    }
    private final CurrentUserMapper mapper;
    public CurrentUserProvider(CurrentUserMapper mapper) { this.mapper = mapper; }
    public CurrentUser currentUser() {
        String id = mapper.findAdministrator();
        List<String> roles = mapper.findAdministratorRoles();
        if (id == null || !roles.contains("ADMIN")) throw new IllegalStateException("임시 관리자 DB 컨텍스트를 찾을 수 없습니다.");
        return new CurrentUser(id, "임시 관리자", List.copyOf(roles), true);
    }
    public String userId() { return currentUser().userId(); }
    public boolean allows(String operation) { currentUser(); return true; }
}
