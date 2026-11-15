package com.sicost.common.history;

import com.sicost.common.validation.InputValidator;
import java.util.Set;

final class RecommendationHistoryPolicy {
    private RecommendationHistoryPolicy() {}
    private static final Set<String> FIELDS=Set.of("USER_ACT_CD","RCMD_EXCL_CD","ACT_USER_ID","ACT_DTTM",
        "expectedRevision","targetRevision","candidateRevision","reasonCode","needSn","confirmedWarnings",
        "assignmentId","tentativeAssignmentId","finalOpportunityId","finalNeedSn","releasedTentativeAssignmentId",
        "year","projectId","developerId","jobCode","startDate","endDate","contractMm","billingUnitPrice","inputType","contractDeveloperId");
    static void requireAllowed(ChangeHistoryWriter.Change change){
        if(change.targetKey()==null||!change.targetKey().matches("[1-9][0-9]{0,11}:[1-5]")
            ||!Set.of("NEW","UPD").contains(change.type())||!FIELDS.contains(change.item())||change.before()!=null)
            throw InputValidator.invalid("history");
    }
}
