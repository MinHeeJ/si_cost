package com.sicost.workforcerecommendations;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.api.ApiException;
import java.time.YearMonth;
import java.util.Set;
import org.springframework.util.MultiValueMap;
import static com.sicost.workforcerecommendations.WorkforceRecommendationDtos.*;

public final class WorkforceRecommendationInput {
    private WorkforceRecommendationInput() {}
    public static Condition create(JsonNode node) {
        if(node==null||!node.isObject())throw invalid("body");
        node.fieldNames().forEachRemaining(k->{if(!Set.of("targetType","targetId","jobCode","startMonth","requestedGradeCode","needSn").contains(k))throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 입력 항목입니다.",k,null);});
        String type=text(node,"targetType",20),id=text(node,"targetId",20),job=text(node,"jobCode",10),month=text(node,"startMonth",7),grade=text(node,"requestedGradeCode",10);
        if(!Set.of("OPPORTUNITY","PROJECT").contains(type))throw invalid("targetType");
        if(!Set.of("JR","MD","SR","EX").contains(grade))throw invalid("requestedGradeCode");
        if(!month.matches("(?!0000)[0-9]{4}-(0[1-9]|1[0-2])"))throw invalid("startMonth");
        try{YearMonth.parse(month);}catch(java.time.DateTimeException e){throw invalid("startMonth");}
        String need=null;
        if("OPPORTUNITY".equals(type)){need=text(node,"needSn",3);if(!need.matches("[1-9][0-9]{0,2}"))throw invalid("needSn");}
        else if(node.hasNonNull("needSn"))throw invalid("needSn");
        return new Condition(type,id,job,month,grade,need);
    }
    public static String id(String id){String value=id.trim();if(!value.matches("[1-9][0-9]{0,11}"))throw invalid("rcmdId");return value;}
    public static void noQuery(MultiValueMap<String,String> query){if(!query.isEmpty())throw new ApiException(400,"UNKNOWN_FIELD","허용되지 않은 조회 항목입니다.",query.keySet().iterator().next(),null);}
    private static String text(JsonNode n,String field,int max){JsonNode v=n.get(field);if(v==null||!v.isTextual())throw invalid(field);String s=v.textValue().trim();if(s.isEmpty()||s.codePointCount(0,s.length())>max)throw invalid(field);return s;}
    public static ApiException invalid(String field){return new ApiException(400,"VALIDATION_ERROR","입력값의 필수 여부와 형식을 확인해 주세요.",field,null);}
}
