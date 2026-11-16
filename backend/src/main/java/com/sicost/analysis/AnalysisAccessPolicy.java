package com.sicost.analysis;

import com.sicost.common.context.CurrentUserProvider;
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
class TemporaryAdministratorAnalysisPolicy implements AnalysisAccessPolicy {
    private final CurrentUserProvider users;
    TemporaryAdministratorAnalysisPolicy(CurrentUserProvider users){this.users=users;}
    public boolean allows(String userId,Target target){return users.userId().equals(userId);}
}
