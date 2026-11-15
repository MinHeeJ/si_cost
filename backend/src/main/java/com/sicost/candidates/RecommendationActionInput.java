package com.sicost.candidates;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.assignmentcandidatesops.AssignmentCandidateInput;
import com.sicost.common.api.ApiException;
import java.util.*;
import static com.sicost.candidates.RecommendationActionDtos.*;

public final class RecommendationActionInput {
    private RecommendationActionInput() {}
    public static Key key(String id,String rank) {
        if(id==null||!id.matches("[1-9][0-9]{0,11}"))throw invalid("rcmdId");
        if(rank==null||!rank.matches("[1-5]"))throw invalid("rank");
        return new Key(id,Integer.parseInt(rank));
    }
    public static void query(Map<String,String> query){if(!query.isEmpty())throw invalid(query.keySet().iterator().next());}
    public static Exclusion exclusion(JsonNode body){
        AssignmentCandidateInput.object(body,Set.of("reasonCode","expectedRevision"));
        String reason=text(body,"reasonCode",4);
        if(!Set.of("SCHD","HOPE","CUST","ETC").contains(reason))throw invalid("reasonCode");
        return new Exclusion(reason,text(body,"expectedRevision",500));
    }
    public static Tentative tentative(JsonNode body){
        AssignmentCandidateInput.object(body,Set.of("needSn","expectedRevision","targetRevision","candidateRevision","confirmedWarnings"));
        String need=text(body,"needSn",3);if(!need.matches("[1-9][0-9]{0,2}"))throw invalid("needSn");
        JsonNode values=body.get("confirmedWarnings");if(values==null||!values.isArray())throw invalid("confirmedWarnings");
        Set<String> seen=new TreeSet<>();
        for(JsonNode value:values){if(!value.isTextual())throw invalid("confirmedWarnings");String code=value.textValue().strip();if(code.isBlank()||code.length()>100||code.chars().anyMatch(Character::isISOControl)||!seen.add(code))throw invalid("confirmedWarnings");}
        return new Tentative(need,text(body,"expectedRevision",500),text(body,"targetRevision",500),text(body,"candidateRevision",500),List.copyOf(seen));
    }
    public static String text(JsonNode body,String field,int max){
        JsonNode value=body.get(field);if(value==null||!value.isTextual())throw invalid(field);
        String text=value.textValue().strip();
        if(text.isBlank()||text.codePointCount(0,text.length())>max||text.chars().anyMatch(Character::isISOControl))throw invalid(field);return text;
    }
    public static ApiException invalid(String field){return new ApiException(400,"VALIDATION_ERROR","입력 항목의 필수값·길이·형식을 확인해 주세요.",field,null);}
}
