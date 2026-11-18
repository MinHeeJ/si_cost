package com.sicost.common.context;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.RequestAttributes;

@Component
public class CurrentUserProvider {
    public record CurrentUser(String userId, String name, List<String> roleIds,
                              boolean developmentMode, String dataScope) {
        public CurrentUser(String userId,String name,List<String> roleIds,boolean developmentMode) {this(userId,name,roleIds,developmentMode,"NONE");}
    }
    private final CurrentUserMapper mapper;
    private com.sicost.permission.PermissionService permissions;
    /** Optional only for hand-assembled contexts: without it the scope is computed as RESTRICTED (fail-closed). */
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setPermissions(com.sicost.permission.PermissionService permissions) {this.permissions=permissions;}
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
        if(com.sicost.permission.PermissionTaskContext.systemCommand())return new CurrentUser("SYSTEM","내부 자동 처리",List.of(),false,"NONE");
        var authentication=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if(authentication==null || !authentication.isAuthenticated() || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
            throw new com.sicost.common.api.ApiException(401,"AUTH_REQUIRED","로그인이 필요합니다.");
        String id=authentication.getName();
        String name = mapper.findName(id);
        List<String> roles = mapper.findActiveRoles(id);
        if(name==null)throw new com.sicost.common.api.ApiException(401,"AUTH_REQUIRED","로그인이 필요합니다.");
        String scope=permissions!=null && permissions.capabilities().scope().all()?"ALL":"RESTRICTED";
        return new CurrentUser(id, name, List.copyOf(roles), false,scope);
    }
    public String userId() { return currentUser().userId(); }
    public boolean allows(String operation) {
        currentUser();String[] parts=operation.split(":",-1);
        return permissions!=null && parts.length==2 && permissions.allows(parts[0],parts[1]);
    }
}
