package com.sicost.authentication;

import com.sicost.common.api.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.*;

/** Opaque server-only proof for accepted user work. No session/credentials in business queues. */
@Service
public class AuthenticationTaskService {
    public static final class Proof {
        private final String userId,passwordHash;
        private final boolean mfaVerified;
        private final Instant expiresAt;
        private final AuthenticationState origin;
        private final com.sicost.permission.PermissionService.Capabilities accepted;
        private Proof(String userId,String hash,boolean mfa,Instant expiry,AuthenticationState origin,com.sicost.permission.PermissionService.Capabilities accepted) {this.userId=userId;passwordHash=hash;mfaVerified=mfa;expiresAt=expiry;this.origin=origin;this.accepted=accepted;}
        @Override public String toString() {return "AuthenticationTaskProof[redacted]";}
    }
    private final AuthenticationService sessions;
    private final AuthenticationMapper accounts;
    private final Clock clock;
    private final ThreadLocal<Proof> current=new ThreadLocal<>();
    private com.sicost.permission.PermissionService permissions;
    private org.springframework.transaction.support.TransactionTemplate fresh;
    @org.springframework.beans.factory.annotation.Autowired
    public void setPermissions(com.sicost.permission.PermissionService permissions,org.springframework.transaction.PlatformTransactionManager manager) {
        this.permissions=permissions;fresh=new org.springframework.transaction.support.TransactionTemplate(manager);
        fresh.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }
    public AuthenticationTaskService(AuthenticationService sessions,AuthenticationMapper accounts,Clock clock) {this.sessions=sessions;this.accounts=accounts;this.clock=clock;}
    public Proof capture(HttpServletRequest request) {
        var state=sessions.validate(request,true);
        synchronized(state) {
            var accepted=request.getAttribute(com.sicost.permission.PermissionOperationRegistry.ACCEPTED);
            var bound=accepted instanceof com.sicost.permission.PermissionService.Capabilities initial
                ?com.sicost.permission.PermissionAcceptanceContext.run(initial,permissions::capabilities):permissions.capabilities();
            return new Proof(state.userId,state.passwordProof,state.mfaVerified,sessions.expires(state),state,bound);
        }
    }
    public void checkpoint(Proof proof) {
        if(proof==null || proof.origin.revoked || !clock.instant().isBefore(proof.expiresAt))throw denied();
        Runnable read=()->{var account=accounts.find(proof.userId);
            if(account==null || !"USE".equals(account.status()) || !Objects.equals(account.passwordHash(),proof.passwordHash) || accounts.administrator(proof.userId) && !proof.mfaVerified)throw denied();};
        if(fresh==null)read.run();else fresh.executeWithoutResult(status->read.run());
    }
    public void validateCurrent() {
        Proof proof=current.get();if(proof!=null) {checkpoint(proof);return;}
        if(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {sessions.validate(attributes.getRequest(),true);return;}
        throw denied();
    }
    public <T> T run(Proof proof,Supplier<T> work) {
        checkpoint(proof);var previous=SecurityContextHolder.getContext();Proof previousProof=current.get();
        try {
            var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(proof.userId,null,List.of()));
            SecurityContextHolder.setContext(context);current.set(proof);
            return com.sicost.permission.PermissionAcceptanceContext.run(proof.accepted,()->{T value=work.get();checkpoint(proof);return value;});
        } finally {if(previousProof==null)current.remove();else current.set(previousProof);SecurityContextHolder.setContext(previous);}
    }
    private static ApiException denied() {return new ApiException(401,"AUTH_REQUIRED","비동기 작업의 인증 정보가 만료되거나 변경되었습니다.");}
}
