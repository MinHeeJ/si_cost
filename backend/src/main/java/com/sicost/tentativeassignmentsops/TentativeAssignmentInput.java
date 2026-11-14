package com.sicost.tentativeassignmentsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.assignmentcandidatesops.AssignmentCandidateInput;
import com.sicost.common.api.ApiException;
import java.util.Set;
import static com.sicost.tentativeassignmentsops.TentativeAssignmentDtos.*;

public final class TentativeAssignmentInput {
    private TentativeAssignmentInput() {}
    public static Release release(JsonNode body) {
        AssignmentCandidateInput.object(body, Set.of("expectedRevision", "reason"));
        String revision;
        try { revision = AssignmentCandidateInput.revision(body); }
        catch (ApiException e) { throw AssignmentCandidateInput.validation(e); }
        String reason = null;
        if (body.has("reason")) {
            if (!body.get("reason").isTextual()) throw invalid("reason");
            reason = body.get("reason").textValue();
            if (reason.codePointCount(0, reason.length()) > 300) throw invalid("reason");
        }
        return new Release(revision, reason);
    }
    private static ApiException invalid(String field) {
        return new ApiException(400, "VALIDATION_ERROR", "입력값을 확인해 주세요.", field, null);
    }
}
