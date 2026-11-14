package com.sicost.assignmentcandidatesops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sicost.assignmentsops.AssignmentActionsInput;
import com.sicost.common.api.ApiException;
import java.util.Set;
import static com.sicost.assignmentcandidatesops.AssignmentCandidateDtos.*;

public final class AssignmentCandidateInput {
    private AssignmentCandidateInput() {}
    public static String id(String id) {
        if(id==null||!id.matches("[1-9][0-9]{0,9}"))throw invalid("id");return id;
    }
    public static String revision(JsonNode body) {
        String value=AssignmentActionsInput.string(body,"expectedRevision",true);
        if(value.isBlank())throw invalid("expectedRevision");return value;
    }
    public static void object(JsonNode body,Set<String> fields) {
        if(body==null||!body.isObject())throw invalid("body");
        body.fieldNames().forEachRemaining(key->{if(!fields.contains(key))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",key,null);});
    }
    public static String drop(JsonNode body) {
        object(body,Set.of("expectedRevision"));
        try{return revision(body);}catch(ApiException e){throw validation(e);}
    }
    public static Confirmation confirm(String pathId,JsonNode body) {
        object(body,Set.of("year","projectId","developerId","jobCode","startDate","endDate","contractMm","billingUnitPrice","inputType","contractDeveloperId","sourceTentId","expectedRevision"));
        try {
            String source=AssignmentActionsInput.string(body,"sourceTentId",true);
            if(!id(pathId).equals(source))throw invalid("sourceTentId");
            String revision=revision(body);
            if(body.has("inputType")&&!"NEW".equals(AssignmentActionsInput.string(body,"inputType",true)))throw invalid("inputType");
            if(body.hasNonNull("contractDeveloperId"))throw invalid("contractDeveloperId");
            ObjectNode ordinary=body.deepCopy();ordinary.remove(java.util.List.of("sourceTentId","expectedRevision"));
            if(body.has("billingUnitPrice")) {
                String price=AssignmentActionsInput.string(body,"billingUnitPrice",true).trim();
                if(!price.matches("(?:[0-9]+|[1-9][0-9]{0,2}(?:,[0-9]{3})+)(?:원)?"))throw invalid("billingUnitPrice");
            }
            return new Confirmation(AssignmentActionsInput.create(ordinary),source,revision);
        } catch(ApiException e){throw validation(e);}
    }
    public static ApiException validation(ApiException e) {
        return e.status()==400?new ApiException(400,"VALIDATION_ERROR",e.getMessage(),e.field(),e.details()):e;
    }
    private static ApiException invalid(String field){return new ApiException(400,"VALIDATION_ERROR","입력값을 확인해 주세요.",field,null);}
}
