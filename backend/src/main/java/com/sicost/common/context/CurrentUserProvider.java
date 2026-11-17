package com.sicost.common.context;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.RequestAttributes;

@Component
public class CurrentUserProvider {
    public record CurrentUser(String userId, String name, List<String> roleIds,
                              boolean developmentMode) {
        public String dataScope() { return "ALL"; }
    }
    private final CurrentUserMapper mapper;
    private static final String REQUEST_USER = CurrentUserProvider.class.getName() + ".currentUser";
    public CurrentUserProvider(CurrentUserMapper mapper) { this.mapper = mapper; }
    public CurrentUser currentUser() {
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        if (request == null) return resolve();
        synchronized (request) {
            Object pinned = request.getAttribute(REQUEST_USER, RequestAttributes.SCOPE_REQUEST);
            if (pinned instanceof CurrentUser user) return user;
            CurrentUser user = resolve();
            request.setAttribute(REQUEST_USER, user, RequestAttributes.SCOPE_REQUEST);
            return user;
        }
    }
    private CurrentUser resolve() {
        String id = mapper.findAdministrator();
        if (id == null) throw new IllegalStateException("임시 관리자 DB 컨텍스트를 찾을 수 없습니다.");
        String name = mapper.findName(id);
        List<String> roles = mapper.findActiveRoles(id);
        if (name == null || !roles.contains("ADMIN")) throw new IllegalStateException("임시 관리자 DB 컨텍스트를 찾을 수 없습니다.");
        return new CurrentUser(id, name, List.copyOf(roles), true);
    }
    public String userId() { return currentUser().userId(); }
    public boolean allows(String operation) { currentUser(); return true; }
}
