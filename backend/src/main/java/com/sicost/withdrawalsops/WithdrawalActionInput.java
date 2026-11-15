package com.sicost.withdrawalsops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.apiops.WithdrawalInput;
import com.sicost.assignmentsops.AssignmentActionsDtos.Change;
import com.sicost.assignmentsops.AssignmentActionsInput;
import com.sicost.common.validation.ProjectInputPolicy;
import java.util.Set;
import org.springframework.util.MultiValueMap;

public final class WithdrawalActionInput {
    private WithdrawalActionInput() {}
    public record Recommendation(int year,String expectedRevision) {}
    public static Change extension(JsonNode body) {
        AssignmentActionsInput.object(body,Set.of("year","extensionPossibility","expectedRevision"));
        return new Change(AssignmentActionsInput.year(body),revision(body),"EXTP",null,null,null,null,null,null,AssignmentActionsInput.possibility(body),null,null,null);
    }
    public static Recommendation recommendation(JsonNode body) {
        AssignmentActionsInput.object(body,Set.of("year","expectedRevision"));
        return new Recommendation(AssignmentActionsInput.year(body),revision(body));
    }
    private static String revision(JsonNode body) {return ProjectInputPolicy.expectedRevision(AssignmentActionsInput.string(body,"expectedRevision",true));}
    public static String id(String value) {return ProjectInputPolicy.text(value,20,"assignmentId");}
    public static void noQuery(MultiValueMap<String,String> query) {
        for(String key:query.keySet())throw ProjectInputPolicy.invalid(key);
    }
    public static WithdrawalInput.Query export(MultiValueMap<String,String> query,String body) {
        for(String key:query.keySet())if(!Set.of("year","period","extension","job").contains(key))throw ProjectInputPolicy.invalid(key);
        return WithdrawalInput.parse(query,body);
    }
}
