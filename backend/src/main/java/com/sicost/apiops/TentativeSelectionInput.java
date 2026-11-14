package com.sicost.apiops;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.assignmentcandidatesops.AssignmentCandidateInput;
import java.time.YearMonth;
import java.util.Set;

public final class TentativeSelectionInput {
    private TentativeSelectionInput(){}
    public record Create(String opportunityId,int needSn,String developerId,YearMonth startMonth,String sourceScreenId,String expectedRevision,String previousOpportunityRevision,boolean warningConfirmed){}
    public static Create parse(JsonNode b){
        AssignmentCandidateInput.object(b,Set.of("opportunityId","needSn","developerId","startMonth","sourceScreenId","expectedRevision","previousOpportunityRevision","warningConfirmed"));
        String opportunity=text(b,"opportunityId",20,true),developer=text(b,"developerId",20,true),start=text(b,"startMonth",7,true),screen=text(b,"sourceScreenId",10,true),revision=text(b,"expectedRevision",500,true),previous=text(b,"previousOpportunityRevision",500,false);
        if(!b.has("needSn")||!b.get("needSn").isIntegralNumber()||!b.get("needSn").canConvertToInt()||b.get("needSn").intValue()<1||b.get("needSn").intValue()>999)throw StaffingQueryInput.invalid("needSn","VALIDATION_ERROR");
        if(!start.matches("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])"))throw StaffingQueryInput.invalid("startMonth","VALIDATION_ERROR");
        if(!Set.of("UI-29","UI-05","UI-30").contains(screen))throw StaffingQueryInput.invalid("sourceScreenId","VALIDATION_ERROR");
        if(b.has("warningConfirmed")&&!b.get("warningConfirmed").isBoolean())throw StaffingQueryInput.invalid("warningConfirmed","VALIDATION_ERROR");
        return new Create(opportunity,b.get("needSn").intValue(),developer,YearMonth.parse(start),screen,revision,previous,b.path("warningConfirmed").asBoolean(false));
    }
    private static String text(JsonNode b,String k,int max,boolean required){
        if(!b.has(k)&&!required)return null;
        if(!b.has(k)||!b.get(k).isTextual())throw StaffingQueryInput.invalid(k,"VALIDATION_ERROR");
        String v=b.get(k).textValue();if(v.isBlank()||v.length()>max||!v.equals(v.strip())||v.chars().anyMatch(Character::isISOControl))throw StaffingQueryInput.invalid(k,"VALIDATION_ERROR");return v;
    }
}
