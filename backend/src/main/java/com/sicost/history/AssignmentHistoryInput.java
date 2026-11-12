package com.sicost.history;

import com.fasterxml.jackson.databind.JsonNode;
import com.sicost.common.validation.AssignmentInputPolicy;
import com.sicost.common.validation.ProjectInputPolicy;
import java.util.*;
import static com.sicost.common.validation.ProjectInputPolicy.*;
import static com.sicost.history.AssignmentHistoryDtos.*;

final class AssignmentHistoryInput {
    static final Set<String> TYPES=Set.of("INPUT","EXT","EARLY","UPRC","RATE","EXTP","KIND","CRCT","CNCL");
    private AssignmentHistoryInput() {}
    static String id(String id) {return text(id,20,"id");}
    static int integer(String value,String field) {
        try {if(value==null||!value.strip().matches("[0-9]+"))throw invalid(field);return Integer.parseInt(value.strip());}
        catch(NumberFormatException e) {throw invalid(field);}
    }
    static void query(Map<String,String> query,Set<String> allowed) {
        query.keySet().forEach(k->{if(!allowed.contains(k))throw invalid(k);});
    }
    static Query query(String id,Map<String,String> values) {
        query(values,Set.of("year","type","page","size"));
        String type=values.containsKey("type")?text(values.get("type"),10,"type"):null;
        if(type!=null&&!TYPES.contains(type))throw invalid("type");
        int page=AssignmentInputPolicy.page(integer(values.getOrDefault("page","1"),"page"));
        int size=AssignmentInputPolicy.size(integer(values.getOrDefault("size","50"),"size"));
        return new Query(id(id),type,page,size,(long)(page-1)*size);
    }
    static int serial(int sn) {if(sn<1||sn>99999)throw invalid("sn");return sn;}
    static Correction correction(JsonNode body) {
        Set<String> allowed=Set.of("year","expectedRevision","correctContent","reasonCode");
        if(body==null||!body.isObject())throw invalid("body");
        body.fieldNames().forEachRemaining(k->{if(!allowed.contains(k))throw invalid(k);});
        if(!body.path("year").isIntegralNumber()||!body.path("year").canConvertToInt())throw invalid("year");
        return new Correction(body.get("year").intValue(),ProjectInputPolicy.expectedRevision(string(body,"expectedRevision")),
            AssignmentInputPolicy.correctionContent(string(body,"correctContent")),text(string(body,"reasonCode"),10,"reasonCode"));
    }
    private static String string(JsonNode body,String field) {
        if(!body.path(field).isTextual())throw invalid(field);return body.get(field).textValue();
    }
}
