package com.sicost.analysis;

import com.sicost.permission.PermissionService;
import com.sicost.authentication.AuthenticationTaskService;
import com.sicost.common.api.ApiException;
import org.springframework.stereotype.Component;
import static com.sicost.analysis.AnalysisAssistantDtos.*;

/** Replacement seam for role/scope policy. Ownership is enforced separately in SQL, including for administrators. */
public interface AnalysisAccessPolicy {
    boolean allows(String userId,Target target);
    default void require(String userId,Scope scope) {
        if(scope.targets().stream().anyMatch(t->!allows(userId,t)))
            throw new ApiException(403,"ANALYSIS_ACCESS_DENIED","이 내용을 볼 권한이 없습니다");
    }
}
@Component
class AuthenticationAnalysisPolicy implements AnalysisAccessPolicy {
    private final PermissionService permissions;
    private final AuthenticationTaskService tasks;
    AuthenticationAnalysisPolicy(PermissionService permissions,AuthenticationTaskService tasks){this.permissions=permissions;this.tasks=tasks;}
    @Override public void require(String userId,Scope scope) {
        tasks.validateCurrent();permissions.require("UI-26","INQ");
        if(!permissions.userId().equals(userId))throw PermissionService.denied();
        AnalysisAccessPolicy.super.require(userId,scope);
    }
    public boolean allows(String userId,Target target) {
        tasks.validateCurrent();
        if(!permissions.userId().equals(userId) || target==null || !permissions.allows("UI-26","INQ"))return false;
        String source=switch(target.targetType()) {
            case "FUNCTION" -> switch(target.targetId()) {
                case "COMPANY" -> "UI-07";
                case "PERSONAL" -> "UI-03";
                case "PROJECT","TURNKEY" -> "UI-04";
                case "SETTLEMENT" -> "UI-19";
                case "BILLING","RECEIVABLE" -> "UI-13";
                case "ASSIGNMENTS" -> "UI-12";
                case "OPPORTUNITIES","REVENUE_FORECAST" -> "UI-29";
                case "WORKFORCE" -> "UI-17";
                case "CASH","CASH_FORECAST" -> "UI-21";
                default -> "";
            };
            case "COMPANY" -> "UI-07";
            case "DEVELOPER" -> target.metricKeys().contains("assignments")?"UI-12":"UI-03";
            case "PROJECT" -> target.metricKeys().stream().anyMatch(java.util.Set.of("billing","receipts","unpaidAmount")::contains)?"UI-13":target.metricKeys().contains("assignments")?"UI-12":"UI-04";
            case "PART" -> "UI-19";
            case "OPPORTUNITY" -> "UI-29";
            default -> "";
        };
        if(!permissions.allows(source,"INQ"))return false;
        boolean assignments=target.targetType().equals("FUNCTION") && target.targetId().equals("ASSIGNMENTS")
            || !target.targetType().equals("FUNCTION") && target.metricKeys()!=null && target.metricKeys().equals(java.util.List.of("assignments"));
        if(assignments) {
            if(target.targetType().equals("FUNCTION"))return permissions.assignmentQueryAllowed();
            if(target.targetType().equals("PROJECT"))return permissions.currentProjectAllowed(target.targetId());
            if(target.targetType().equals("DEVELOPER"))return permissions.assignmentPersonAllowed(target.targetId());
            return false;
        }
        return permissions.capabilities().scope().all() && permissions.capabilities().sensitive();
    }
}
