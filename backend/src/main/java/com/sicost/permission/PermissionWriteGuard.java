package com.sicost.permission;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.authentication.AuthenticationTaskService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;
import org.springframework.web.context.request.*;
import org.springframework.web.servlet.HandlerMapping;

/** Recheck independently committed authorization, not a transaction's stale RBAC snapshot. */
@Service
public class PermissionWriteGuard {
    private static final String PROOF=PermissionWriteGuard.class.getName()+".proof";
    public static final String SCOPE=PermissionWriteGuard.class.getName()+".scope";
    private final ObjectProvider<AuthenticationTaskService> authentication;
    private final ObjectProvider<PermissionOperationRegistry> operations;
    private final ObjectProvider<PermissionBusinessPolicy> business;
    private final ObjectProvider<PermissionService> policies;
    private final TransactionTemplate fresh;
    private final ThreadLocal<Boolean> checking=new ThreadLocal<>();
    public PermissionWriteGuard(ObjectProvider<AuthenticationTaskService> authentication,ObjectProvider<PermissionOperationRegistry> operations,ObjectProvider<PermissionBusinessPolicy> business,ObjectProvider<PermissionService> policies,PlatformTransactionManager manager) {
        this.authentication=authentication;this.operations=operations;this.business=business;
        this.policies=policies;
        fresh=new TransactionTemplate(manager);fresh.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }
    public void beforeStatement(String statementId) {
        if(Boolean.TRUE.equals(checking.get()))return;
        if(!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes))return;
        var request=attributes.getRequest();
        if(!(request.getAttribute(PermissionOperationRegistry.OPERATION) instanceof PermissionOperationRegistry.Operation op) || java.util.Set.of("PUBLIC","AUTH").contains(op.policy()))return;
        if(statementId.startsWith("com.sicost.authentication.") && op.policy().equals("OWN"))return;
        var tasks=authentication.getObject();
        var proof=(AuthenticationTaskService.Proof)request.getAttribute(PROOF);
        if(proof==null) {
            checking.set(true);
            try {proof=tasks.capture(request);request.setAttribute(PROOF,proof);}
            finally {checking.remove();}
        }
        AuthenticationTaskService.Proof accepted=proof;
        Runnable check=()->fresh.executeWithoutResult(status->{
            checking.set(true);
            try {
                tasks.checkpoint(accepted);
                operations.getObject().preHandle(request,attributes.getResponse(),request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE));
                if(request.getAttribute(PermissionBusinessPolicy.BODY) instanceof JsonNode body)business.getObject().check(request,body);
                var policy=policies.getObject();request.setAttribute(SCOPE,policy.capabilities().scope());
            } finally {checking.remove();}
        });
        check.run();
        if(TransactionSynchronizationManager.isSynchronizationActive() && TransactionSynchronizationManager.getSynchronizations().stream().noneMatch(sync->sync instanceof GuardSynchronization))
            TransactionSynchronizationManager.registerSynchronization(new GuardSynchronization(check));
    }
    private record GuardSynchronization(Runnable check) implements TransactionSynchronization {
        @Override public void beforeCommit(boolean readOnly) {if(!readOnly)check.run();}
    }
}
